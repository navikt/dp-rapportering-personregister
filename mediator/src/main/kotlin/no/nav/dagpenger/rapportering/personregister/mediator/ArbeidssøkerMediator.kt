package no.nav.dagpenger.rapportering.personregister.mediator

import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.ActionTimer
import no.nav.dagpenger.rapportering.personregister.mediator.service.ArbeidssøkerService
import no.nav.dagpenger.rapportering.personregister.mediator.service.PersonService
import no.nav.dagpenger.rapportering.personregister.modell.Arbeidssøkerperiode
import no.nav.dagpenger.rapportering.personregister.modell.ArbeidssøkerperiodeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.AvsluttetArbeidssøkerperiodeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.PersonObserver
import no.nav.dagpenger.rapportering.personregister.modell.StartetArbeidssøkerperiodeHendelse

class ArbeidssøkerMediator(
    private val arbeidssøkerService: ArbeidssøkerService,
    private val personRepository: PersonRepository,
    private val personService: PersonService,
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
                    logger.info { "Personen er ikke arbeidssøker" }
                }
            } catch (e: Exception) {
                logger.error(e) { "Feil ved behandling av arbeidssøkerperiode" }
            }
        }

    private fun behandle(arbeidssøkerHendelse: ArbeidssøkerperiodeHendelse) {
        logger.info { "Behandler arbeidssøkerhendelse: ${arbeidssøkerHendelse.referanseId}" }

        personService
            .hentPerson(arbeidssøkerHendelse.ident)
            ?.let { person ->
                personObservers.forEach { person.addObserver(it) }
                person.behandle(arbeidssøkerHendelse)
                personRepository.oppdaterPerson(person)
            } ?: logger.info { "Personen hendelsen gjelder for finnes ikke i databasen." }
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
