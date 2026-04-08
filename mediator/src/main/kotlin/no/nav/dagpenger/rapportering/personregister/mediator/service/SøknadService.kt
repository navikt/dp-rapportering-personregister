package no.nav.dagpenger.rapportering.personregister.mediator.service

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.ArbeidssøkerMediator
import no.nav.dagpenger.rapportering.personregister.mediator.db.OptimisticLockingException
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.ActionTimer
import no.nav.dagpenger.rapportering.personregister.modell.AnsvarligSystem
import no.nav.dagpenger.rapportering.personregister.modell.erArbeidssøker
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.SøknadHendelse
import no.nav.dagpenger.rapportering.personregister.modell.oppfyllerKrav
import no.nav.dagpenger.rapportering.personregister.modell.sendOvertakelsesmelding
import no.nav.dagpenger.rapportering.personregister.modell.sendStartMeldingTilMeldekortregister
import no.nav.dagpenger.rapportering.personregister.modell.vurderNyStatus

class SøknadService(
    private val personService: PersonService,
    private val personRepository: PersonRepository,
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
                person.sendStartMeldingTilMeldekortregister(fraOgMed = søknadHendelse.startDato, skalMigreres = false)
            }

            val nyStatus = person.vurderNyStatus()
            if (nyStatus != person.status && person.oppfyllerKrav) {
                person.setStatus(nyStatus)
                person.sendOvertakelsesmelding()
            }

            try {
                personRepository.oppdaterPerson(person)
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

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
