package no.nav.dagpenger.rapportering.personregister.mediator

import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.ActionTimer
import no.nav.dagpenger.rapportering.personregister.mediator.service.ArbeidssøkerService
import no.nav.dagpenger.rapportering.personregister.modell.Arbeidssøkerperiode
import no.nav.dagpenger.rapportering.personregister.modell.ArbeidssøkerperiodeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.AvsluttetArbeidssøkerperiodeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.PersonObserver
import no.nav.dagpenger.rapportering.personregister.modell.StartetArbeidssøkerperiodeHendelse

class ArbeidssøkerMediator(
    private val arbeidssøkerService: ArbeidssøkerService,
    private val personRepository: PersonRepository,
    private val personObservers: List<PersonObserver>,
    private val actionTimer: ActionTimer,
) {
    fun behandle(arbeidssøkerperiode: Arbeidssøkerperiode) =
        actionTimer.timedAction("behandle_arbeidssokerperiode") {
            if (arbeidssøkerperiode.avsluttet == null) {
                behandle(
                    StartetArbeidssøkerperiodeHendelse(
                        periodeId = arbeidssøkerperiode.periodeId,
                        ident = arbeidssøkerperiode.ident,
                        startet = arbeidssøkerperiode.startet,
                    ),
                )
            } else {
                behandle(
                    AvsluttetArbeidssøkerperiodeHendelse(
                        periodeId = arbeidssøkerperiode.periodeId,
                        ident = arbeidssøkerperiode.ident,
                        startet = arbeidssøkerperiode.startet,
                        avsluttet = arbeidssøkerperiode.avsluttet!!,
                    ),
                )
            }
        }

    fun behandle(ident: String) =
        actionTimer.timedAction("behandle_arbeidssoker") {
            try {
                val arbeidssøkerperiode = runBlocking { arbeidssøkerService.hentSisteArbeidssøkerperiode(ident) }
                if (arbeidssøkerperiode != null) {
                    behandle(arbeidssøkerperiode)
                } else {
                    sikkerlogg.info { "Personen er ikke arbeidssøker" }
                }
            } catch (e: Exception) {
                sikkerlogg.error(e) { "Feil ved behandling av arbeidssøkerperiode for ident $ident" }
            }
        }

    private fun behandle(arbeidssøkerHendelse: ArbeidssøkerperiodeHendelse) {
        sikkerlogg.info { "Behandler arbeidssøkerhendelse: $arbeidssøkerHendelse" }

        personRepository
            .hentPerson(arbeidssøkerHendelse.ident)
            ?.let { person ->
                personObservers.forEach { person.addObserver(it) }
                person.behandle(arbeidssøkerHendelse)
                personRepository.oppdaterPerson(person)
            } ?: sikkerlogg.info { "Personen hendelsen gjelder for finnes ikke i databasen." }
    }

    companion object {
        val sikkerlogg = KotlinLogging.logger("tjenestekall")
    }
}
