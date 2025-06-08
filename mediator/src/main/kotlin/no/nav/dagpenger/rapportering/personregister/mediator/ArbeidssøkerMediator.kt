package no.nav.dagpenger.rapportering.personregister.mediator

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.db.OptimisticLockingException
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.ActionTimer
import no.nav.dagpenger.rapportering.personregister.mediator.service.ArbeidssøkerService
import no.nav.dagpenger.rapportering.personregister.mediator.service.PersonService
import no.nav.dagpenger.rapportering.personregister.modell.Arbeidssøkerperiode
import no.nav.dagpenger.rapportering.personregister.modell.ArbeidssøkerperiodeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.AvsluttetArbeidssøkerperiodeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.PersonObserver
import no.nav.dagpenger.rapportering.personregister.modell.StartetArbeidssøkerperiodeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.gjeldende
import no.nav.paw.bekreftelse.paavegneav.v1.PaaVegneAv
import no.nav.paw.bekreftelse.paavegneav.v1.vo.Start
import no.nav.paw.bekreftelse.paavegneav.v1.vo.Stopp
import kotlin.time.Duration.Companion.seconds

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

    suspend fun behandle(
        paVegneAv: PaaVegneAv,
        counter: Int = 1,
        withDelay: Boolean = true,
    ) {
        if (withDelay) {
            logger.info("Behandler PaaVegneAv-melding. Venter i 1 sekunder.")
            delay(1.seconds)
        }
        logger.info { "Behandler PaaVegneAv-melding: for periodeId: ${paVegneAv.periodeId}" }

        val person =
            try {
                personRepository
                    .hentPersonMedPeriodeId(paVegneAv.periodeId)
                    ?.also { person ->
                        if (person.observers.isEmpty()) {
                            personObservers.forEach { person.addObserver(it) }
                        }
                    }
            } catch (e: Exception) {
                logger.error(e) { "Feil ved henting av person ved behandling med periodeId ${paVegneAv.periodeId}" }
                return
            }

        if (person == null) {
            logger.error { "Fant ikke person med periodeId ${paVegneAv.periodeId} i databasen." }
            return
        }

        if (person.arbeidssøkerperioder.none { it.periodeId == paVegneAv.periodeId }) {
            logger.error { "Person har ingen arbeidssøkerperiode med periodeId ${paVegneAv.periodeId}. Dropper meldingen." }
            return
        }

        when (paVegneAv.handling) {
            is Start -> {
                logger.info { "Behandler PaaVegneAv-melding med start for periodeId: ${paVegneAv.periodeId}" }
                if (person.arbeidssøkerperioder.gjeldende?.overtattBekreftelse == true) {
                    logger.info { "Person har allerede overtatt bekreftelse for periodeId: ${paVegneAv.periodeId}" }
                } else {
                    person.observers.forEach { it.overtattArbeidssøkerbekreftelse(person, paVegneAv.periodeId) }
                }
            }

            is Stopp -> {
                logger.info { "Behandler PaaVegneAv-melding med stopp for periodeId: ${paVegneAv.periodeId}" }
                if (person.arbeidssøkerperioder.gjeldende?.overtattBekreftelse == false) {
                    logger.info { "Person har allerede frasagt bekreftelse for periodeId: ${paVegneAv.periodeId}" }
                } else {
                    person.observers.forEach { it.frasagtArbeidssøkerbekreftelse(person, paVegneAv.periodeId) }
                }
            }

            else -> {
                logger.warn { "Ukjent handling i PaaVegneAv: ${paVegneAv.handling}. PeriodeId ${paVegneAv.periodeId}" }
            }
        }

        logger.info(
            "Oppdaterer person med periodeId ${paVegneAv.periodeId} og overtattBekreftelse = ${person.arbeidssøkerperioder.gjeldende?.overtattBekreftelse}",
        )
        try {
            personRepository.oppdaterPerson(person)
        } catch (e: OptimisticLockingException) {
            logger.info(
                e,
            ) { "Optimistisk låsing feilet ved oppdatering av person med periodeId ${paVegneAv.periodeId}. Counter: $counter" }
            behandle(paVegneAv, counter + 1, false)
        }
    }

    private fun behandle(
        arbeidssøkerHendelse: ArbeidssøkerperiodeHendelse,
        counter: Int = 1,
    ) {
        logger.info { "Behandler arbeidssøkerhendelse: ${arbeidssøkerHendelse.referanseId}" }

        personService
            .hentPerson(arbeidssøkerHendelse.ident)
            ?.let { person ->
                if (person.observers.isEmpty()) {
                    personObservers.forEach { person.addObserver(it) }
                }
                logger.info {
                    "Behandler arbeidssøkerhendelse for person med meldeplikt = ${person.meldeplikt} og meldegruppe = ${person.meldegruppe}"
                }
                person.behandle(arbeidssøkerHendelse)
                try {
                    personRepository.oppdaterPerson(person)
                } catch (e: OptimisticLockingException) {
                    logger.info(
                        e,
                    ) {
                        "Optimistisk låsing feilet ved oppdatering av person med periodeId ${arbeidssøkerHendelse.periodeId}. Counter: $counter"
                    }
                    behandle(arbeidssøkerHendelse, counter + 1)
                }
            } ?: logger.info { "Personen hendelsen gjelder for finnes ikke i databasen." }
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
