package no.nav.dagpenger.rapportering.personregister.mediator

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.rapportering.personregister.mediator.db.OptimisticLockingException
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.ActionTimer
import no.nav.dagpenger.rapportering.personregister.mediator.service.PersonService
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.PersonObserver
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.AnnenMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.DagpengerMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.Hendelse
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.MeldesyklusErPassertHendelse
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.PersonIkkeDagpengerSynkroniseringHendelse
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.PersonSynkroniseringHendelse
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.SøknadHendelse
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.VedtakHendelse
import java.time.LocalDateTime

class PersonMediator(
    private val personRepository: PersonRepository,
    private val personService: PersonService,
    private val arbeidssøkerMediator: ArbeidssøkerMediator,
    private val personObservers: List<PersonObserver>,
    private val meldepliktMediator: MeldepliktMediator,
    private val actionTimer: ActionTimer,
) {
    fun behandle(
        søknadHendelse: SøknadHendelse,
        counter: Int = 1,
    ): Unit =
        actionTimer.timedAction("behandle_SoknadHendelse") {
            logger.info { "Behandler søknadshendelse: ${søknadHendelse.referanseId}" }
            hentEllerOpprettPerson(søknadHendelse.ident)
                .also { person ->
                    person.behandle(søknadHendelse)
                    try {
                        personRepository.oppdaterPerson(person)
                    } catch (e: OptimisticLockingException) {
                        logger.info(e) {
                            "Optimistisk låsing feilet ved oppdatering av person med periodeId ${søknadHendelse.referanseId}. Counter: $counter"
                        }
                        behandle(søknadHendelse, counter + 1)
                    }
                    arbeidssøkerMediator.behandle(søknadHendelse.ident)
                }
        }

    fun behandle(
        vedtakHendelse: VedtakHendelse,
        counter: Int = 1,
    ): Unit =
        actionTimer.timedAction("behandle_VedtakHendelse") {
            logger.info { "Behandler vedtakshendelse: ${vedtakHendelse.referanseId}" }
            hentEllerOpprettPerson(vedtakHendelse.ident)
                .also { person ->
                    person.behandle(vedtakHendelse)
                    try {
                        personRepository.oppdaterPerson(person)
                    } catch (e: OptimisticLockingException) {
                        logger.info(e) {
                            "Optimistisk låsing feilet ved oppdatering av person med behandlingId ${vedtakHendelse.referanseId}. Counter: $counter"
                        }
                        behandle(vedtakHendelse, counter + 1)
                    }
                }
        }

    fun behandle(
        hendelse: DagpengerMeldegruppeHendelse,
        counter: Int = 1,
    ): Unit =
        actionTimer.timedAction("behandle_DagpengerMeldegruppeHendelse") {
            logger.info { "Behandler dagpenger meldegruppe hendelse: ${hendelse.referanseId}" }
            if (hendelse.sluttDato?.isBefore(LocalDateTime.now()) == true) {
                logger.info("DagpengerMeldegruppeHendelse med referanseId ${hendelse.referanseId} gjelder tilbake i tid. Ignorerer.")
            } else {
                hentEllerOpprettPerson(hendelse.ident)
                    .also { person ->
                        person.behandle(hendelse)
                        try {
                            personRepository.oppdaterPerson(person)
                        } catch (e: OptimisticLockingException) {
                            logger.info(e) {
                                "Optimistisk låsing feilet ved oppdatering av person med periodeId ${hendelse.referanseId}. Counter: $counter"
                            }
                            behandle(hendelse, counter + 1)
                        }
                        if (!person.meldeplikt) {
                            runBlocking { meldepliktMediator.behandle(hendelse.ident, hendelse.harMeldtSeg) }
                        }
                        arbeidssøkerMediator.behandle(hendelse.ident)
                    }
            }
        }

    fun behandle(hendelse: AnnenMeldegruppeHendelse) =
        actionTimer.timedAction("behandle_AnnenMeldegruppeHendelse") {
            logger.info { "Behandler annen meldegruppe hendelse: ${hendelse.referanseId}" }
            if (hendelse.sluttDato?.isBefore(LocalDateTime.now()) == true) {
                logger.info("AnnenMeldegruppeHendelse med referanseId ${hendelse.referanseId} gjelder tilbake i tid. Ignorerer.")
            } else {
                behandleHendelse(hendelse)
            }
        }

    suspend fun behandle(
        hendelse: PersonSynkroniseringHendelse,
        counter: Int = 1,
    ): Unit =
        actionTimer.coTimedAction("behandle_PersonSynkroniseringHendelse") {
            logger.info { "Behandler PersonSynkroniseringHendelse: ${hendelse.referanseId}" }
            hentEllerOpprettPerson(hendelse.ident)
                .also { person ->
                    person.behandle(hendelse)
                    try {
                        personRepository.oppdaterPerson(person)
                    } catch (e: OptimisticLockingException) {
                        logger.info(e) {
                            "Optimistisk låsing feilet ved oppdatering av person med periodeId ${hendelse.referanseId}. Counter: $counter"
                        }
                        behandle(hendelse, counter + 1)
                    }
                    arbeidssøkerMediator.behandle(person.ident)
                }
        }

    suspend fun behandle(
        hendelse: PersonIkkeDagpengerSynkroniseringHendelse,
        counter: Int = 1,
    ): Unit =
        actionTimer.coTimedAction("behandle_PersonIkkeDagpengerSynkroniseringHendelse") {
            logger.info { "Behandler PersonIkkeDagpengerSynkroniseringHendelse: ${hendelse.referanseId}" }
            hentEllerOpprettPerson(hendelse.ident)
                .also { person ->
                    person.behandle(hendelse)
                    try {
                        personRepository.oppdaterPerson(person)
                    } catch (e: OptimisticLockingException) {
                        logger.info(e) {
                            "Optimistisk låsing feilet ved oppdatering av person med periodeId ${hendelse.referanseId}. Counter: $counter"
                        }
                        behandle(hendelse, counter + 1)
                    }
                    arbeidssøkerMediator.behandle(person.ident)
                }
        }

    fun behandle(
        hendelse: MeldesyklusErPassertHendelse,
        counter: Int = 1,
    ): Unit =
        actionTimer.timedAction("behandle_MeldesyklusErPassertHendelse") {
            logger.info { "Behandler MeldesyklusErPassertHendelse: ${hendelse.referanseId}" }
            hentEllerOpprettPerson(hendelse.ident)
                .also { person ->
                    person.behandle(hendelse)
                    try {
                        personRepository.oppdaterPerson(person)
                    } catch (e: OptimisticLockingException) {
                        logger.info(e) {
                            "Optimistisk låsing feilet ved oppdatering av person med periodeId ${hendelse.referanseId}. Counter: $counter"
                        }
                        behandle(hendelse, counter + 1)
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
                            "Optimistisk låsing feilet ved oppdatering av person med periodeId ${hendelse.referanseId}. Counter: $counter"
                        }
                        behandleHendelse(hendelse, counter + 1)
                    }
                    logger.info { "Hendelse behandlet: ${hendelse.referanseId}" }
                }
        } catch (e: Exception) {
            logger.info(e) { "Feil ved behandling av hendelse: ${hendelse.referanseId}" }
        }
    }

    private fun hentEllerOpprettPerson(ident: String): Person =
        personService
            .hentPerson(ident) ?: Person(ident)
            .also { person ->
                if (person.observers.isEmpty()) {
                    personObservers.forEach { observer -> person.addObserver(observer) }
                }
                personRepository.lagrePerson(person)
            }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
