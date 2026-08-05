package no.nav.dagpenger.rapportering.personregister.mediator.service

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.ArbeidssøkerMediator
import no.nav.dagpenger.rapportering.personregister.mediator.api.PersonNotFoundException
import no.nav.dagpenger.rapportering.personregister.mediator.db.OptimisticLockingException
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.ActionTimer
import no.nav.dagpenger.rapportering.personregister.modell.AnsvarligSystem
import no.nav.dagpenger.rapportering.personregister.modell.erArbeidssøker
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.SøknadHendelse
import no.nav.dagpenger.rapportering.personregister.modell.oppfyllerKrav
import no.nav.dagpenger.rapportering.personregister.modell.sendOvertakelsesmelding
import no.nav.dagpenger.rapportering.personregister.modell.sendStartMeldingTilMeldekortregister
import no.nav.dagpenger.rapportering.personregister.modell.vurderNyStatus
import java.time.LocalDateTime
import java.util.UUID

private val logger = KotlinLogging.logger {}
private val sikkerlogg = KotlinLogging.logger("tjenestekall")

class SøknadService(
    private val personService: PersonService,
    private val arbeidssøkerMediator: ArbeidssøkerMediator,
    private val actionTimer: ActionTimer,
) {
    fun behandle(
        søknadHendelse: SøknadHendelse,
        counter: Int = 1,
    ) {
        actionTimer.timedAction("behandle_SoknadHendelse") {
            logger.info { "Behandler søknadshendelse: ${søknadHendelse.referanseId}" }

            val person = personService.hentEllerOpprettPerson(søknadHendelse.ident)

            if (person.hendelser.any { it.referanseId == søknadHendelse.referanseId }) {
                logger.info { "Søknadshendelse ${søknadHendelse.referanseId} er allerede behandlet. Hopper over." }
                return@timedAction
            }
            person.hendelser.add(søknadHendelse)

            if (person.ansvarligSystem == AnsvarligSystem.DP && person.erArbeidssøker) {
                person.setHarRettTilDp(true)
                person.sendStartMeldingTilMeldekortregister(
                    fraOgMed = søknadHendelse.startDato,
                    skalMigreres = false,
                    korrelasjonsId = søknadHendelse.korrelasjonsId,
                )
            }

            val nyStatus = person.vurderNyStatus()
            if (nyStatus != person.status && person.oppfyllerKrav) {
                person.setStatus(nyStatus)
                person.sendOvertakelsesmelding(søknadHendelse.korrelasjonsId)
            }

            try {
                personService.oppdaterPerson(person)
            } catch (e: OptimisticLockingException) {
                logger.info(e) {
                    "Optimistisk låsing feilet ved oppdatering av person med referanseId ${søknadHendelse.referanseId}. Counter: $counter"
                }
                behandle(søknadHendelse, counter + 1)
                return@timedAction
            }

            arbeidssøkerMediator.behandle(søknadHendelse.ident)
        }
    }

    fun hentSøknadInnsendtTidspunkt(
        ident: String,
        søknadId: UUID,
    ): LocalDateTime =
        personService.hentPerson(ident)?.let {
            it.hendelser.firstOrNull { hendelse -> hendelse is SøknadHendelse && hendelse.referanseId == søknadId.toString() }?.startDato
                ?: throw SøknadIkkeFunnetException(søknadId)
        } ?: run {
            sikkerlogg.warn { "Fant ikke person ident=${ident.take(11)}" }
            throw PersonNotFoundException()
        }
}

data class SøknadIkkeFunnetException(
    private val søknadId: UUID,
    override val message: String = "Fant ikke søknadId=$søknadId",
) : Exception(message)
