package no.nav.dagpenger.rapportering.personregister.mediator

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.connector.MeldepliktConnector
import no.nav.dagpenger.rapportering.personregister.mediator.db.OptimisticLockingException
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.ActionTimer
import no.nav.dagpenger.rapportering.personregister.mediator.service.PersonService
import no.nav.dagpenger.rapportering.personregister.modell.Hendelse
import no.nav.dagpenger.rapportering.personregister.modell.MeldepliktHendelse
import no.nav.dagpenger.rapportering.personregister.modell.PersonObserver
import java.time.LocalDateTime
import java.util.UUID

class MeldepliktMediator(
    private val personRepository: PersonRepository,
    private val personService: PersonService,
    private val personObservers: List<PersonObserver>,
    private val meldepliktConnector: MeldepliktConnector,
    private val actionTimer: ActionTimer,
) {
    fun behandle(hendelse: MeldepliktHendelse) =
        actionTimer.timedAction("behandle_MeldepliktHendelse") {
            logger.info { "Behandler meldeplikthendelse: ${hendelse.referanseId} med status: ${hendelse.statusMeldeplikt}" }
            if (hendelse.sluttDato?.isBefore(LocalDateTime.now()) == true) {
                logger.info("MeldepliktHendelse med referanseId ${hendelse.referanseId} gjelder tilbake i tid. Ignorerer.")
            } else {
                behandleHendelse(hendelse)
            }
        }

    suspend fun behandle(
        ident: String,
        harMeldtSeg: Boolean,
        withDelay: Boolean = true,
    ) {
        // Delay for å la eventuell melding om meldeplikt fra Arena bli behandlet først
        if (withDelay) {
            delay(1000)
        }
        actionTimer.timedAction("behandle_hentMeldeplikt") {
            logger.info { "Henter meldeplikt for ident" }
            try {
                personService
                    .hentPerson(ident)
                    ?.let { person ->
                        if (!person.meldeplikt) {
                            val meldeplikt = runBlocking { meldepliktConnector.hentMeldeplikt(ident) }
                            logger.info { "Hentet meldeplikt status: $meldeplikt. Nåværende meldeplikt for person: ${person.meldeplikt}" }
                            if (person.meldeplikt != meldeplikt) {
                                MeldepliktHendelse(
                                    ident = person.ident,
                                    referanseId = UUID.randomUUID().toString(),
                                    dato = LocalDateTime.now(),
                                    startDato = LocalDateTime.now(),
                                    sluttDato = null,
                                    statusMeldeplikt = meldeplikt,
                                    harMeldtSeg = harMeldtSeg,
                                ).also { hendelse ->
                                    behandleHendelse(hendelse)
                                }
                            }
                        }
                    }
            } catch (e: Exception) {
                logger.error(e) { "Feil ved henting av meldeplikt for ident" }
            }
        }
    }

    private fun behandleHendelse(
        hendelse: Hendelse,
        counter: Int = 1,
    ) {
        try {
            personService
                .hentPerson(hendelse.ident)
                ?.let { person ->
                    if (person.observers.isEmpty()) {
                        personObservers.forEach { person.addObserver(it) }
                    }
                    person.behandle(hendelse)
                    try {
                        personRepository.oppdaterPerson(person)
                    } catch (e: OptimisticLockingException) {
                        logger.info(e) {
                            "Optimistisk låsing feilet ved oppdatering for hendelse med refId ${hendelse.referanseId}. Counter: $counter"
                        }
                        behandleHendelse(hendelse, counter + 1)
                    }
                    logger.info { "Hendelse behandlet: ${hendelse.referanseId}" }
                }
        } catch (e: Exception) {
            logger.info(e) { "Feil ved behandling av hendelse: ${hendelse.referanseId}" }
        }
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
