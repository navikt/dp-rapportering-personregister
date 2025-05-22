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
import java.time.LocalDateTime

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

    fun ryddArbeidssøkerperioder(ident: String) {
        val sisteArbeidssøkerperiode = runBlocking { arbeidssøkerService.hentSisteArbeidssøkerperiode(ident) }
        sikkerLogg.info("Siste arbeidssøkerperiode for $ident: $sisteArbeidssøkerperiode")
        if (sisteArbeidssøkerperiode == null) {
            sikkerLogg.info("Ingen arbeidssøkerperioder funnet for $ident")
            return
        }
        val person =
            personService.hentPerson(ident)?.also { person ->
                if (person.observers.isEmpty()) {
                    personObservers.forEach { observer -> person.addObserver(observer) }
                }
            }
        if (person == null) {
            sikkerLogg.info("Ingen person funnet for $ident")
            return
        }
        person.arbeidssøkerperioder
            .filterNot { it.periodeId == sisteArbeidssøkerperiode.periodeId }
            .forEach { arbeidsPeriode ->
                if (arbeidsPeriode.avsluttet != null) {
                    behandle(arbeidsPeriode.copy(avsluttet = LocalDateTime.now()))
                }
            }
        behandle(person.ident)
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
        private val sikkerLogg = KotlinLogging.logger("tjenestekall")
    }
}
