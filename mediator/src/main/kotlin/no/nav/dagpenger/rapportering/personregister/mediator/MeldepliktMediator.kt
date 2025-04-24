package no.nav.dagpenger.rapportering.personregister.mediator

import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.connector.MeldepliktConnector
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.ActionTimer
import no.nav.dagpenger.rapportering.personregister.modell.Hendelse
import no.nav.dagpenger.rapportering.personregister.modell.MeldepliktHendelse
import no.nav.dagpenger.rapportering.personregister.modell.PersonObserver
import java.time.LocalDateTime
import java.util.UUID

class MeldepliktMediator(
    private val personRepository: PersonRepository,
    private val personObservers: List<PersonObserver>,
    private val meldepliktConnector: MeldepliktConnector,
    private val actionTimer: ActionTimer,
) {
    fun behandle(hendelse: MeldepliktHendelse) =
        actionTimer.timedAction("behandle_MeldepliktHendelse") {
            logger.info { "Behandler meldeplikthendelse: ${hendelse.referanseId}" }
            if (hendelse.sluttDato?.isBefore(LocalDateTime.now()) == true) {
                logger.info("MeldepliktHendelse med referanseId ${hendelse.referanseId} gjelder tilbake i tid. Ignorerer.")
            } else {
                behandleHendelse(hendelse)
            }
        }

    fun behandle(ident: String) {
        actionTimer.timedAction("behandle_hentMeldeplikt") {
            logger.info { "Henter meldeplikt for ident" }
            try {
                personRepository
                    .hentPerson(ident)
                    ?.let { person ->
                        if (!person.meldeplikt) {
                            val meldeplikt = runBlocking { meldepliktConnector.hentMeldeplikt(ident) }
                            if (person.meldeplikt != meldeplikt) {
                                MeldepliktHendelse(
                                    ident = ident,
                                    referanseId = UUID.randomUUID().toString(),
                                    dato = LocalDateTime.now(),
                                    startDato = LocalDateTime.now(),
                                    sluttDato = null,
                                    statusMeldeplikt = meldeplikt,
                                    harMeldtSeg = true,
                                ).also { hendelse ->
                                    behandleHendelse(hendelse)
                                }
                            }
                        }
                    }
            } catch (e: Exception) {
                logger.error(e) { "Feil ved henting av meldeplikt for ident: $ident" }
            }
        }
    }

    private fun behandleHendelse(hendelse: Hendelse) {
        try {
            personRepository
                .hentPerson(hendelse.ident)
                ?.let { person ->
                    if (person.observers.isEmpty()) {
                        personObservers.forEach { person.addObserver(it) }
                    }
                    person.behandle(hendelse)
                    personRepository.oppdaterPerson(person)
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
