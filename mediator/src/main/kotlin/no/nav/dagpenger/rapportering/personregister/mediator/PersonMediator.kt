package no.nav.dagpenger.rapportering.personregister.mediator

import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.ActionTimer
import no.nav.dagpenger.rapportering.personregister.mediator.service.PersonService
import no.nav.dagpenger.rapportering.personregister.modell.AnnenMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.DagpengerMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.Hendelse
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.PersonObserver
import no.nav.dagpenger.rapportering.personregister.modell.PersonSynkroniseringHendelse
import no.nav.dagpenger.rapportering.personregister.modell.SøknadHendelse
import no.nav.dagpenger.rapportering.personregister.modell.overtaArbeidssøkerBekreftelse
import java.time.LocalDateTime

class PersonMediator(
    private val personRepository: PersonRepository,
    private val personService: PersonService,
    private val arbeidssøkerMediator: ArbeidssøkerMediator,
    private val personObservers: List<PersonObserver>,
    private val meldepliktMediator: MeldepliktMediator,
    private val actionTimer: ActionTimer,
) {
    fun behandle(søknadHendelse: SøknadHendelse) =
        actionTimer.timedAction("behandle_SoknadHendelse") {
            logger.info { "Behandler søknadshendelse: ${søknadHendelse.referanseId}" }
            hentEllerOpprettPerson(søknadHendelse.ident)
                .also {
                    it.behandle(søknadHendelse)
                    personRepository.oppdaterPerson(it)
                    arbeidssøkerMediator.behandle(søknadHendelse.ident)
                }
        }

    fun behandle(hendelse: DagpengerMeldegruppeHendelse) =
        actionTimer.timedAction("behandle_DagpengerMeldegruppeHendelse") {
            logger.info { "Behandler dagpenger meldegruppe hendelse: ${hendelse.referanseId}" }
            if (hendelse.sluttDato?.isBefore(LocalDateTime.now()) == true) {
                logger.info("DagpengerMeldegruppeHendelse med referanseId ${hendelse.referanseId} gjelder tilbake i tid. Ignorerer.")
            } else {
                hentEllerOpprettPerson(hendelse.ident)
                    .also { person ->
                        person.behandle(hendelse)
                        personRepository.oppdaterPerson(person)
                        runBlocking { meldepliktMediator.behandle(hendelse.ident) }
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

    suspend fun behandle(hendelse: PersonSynkroniseringHendelse) =
        actionTimer.coTimedAction("behandle_PersonSynkroniseringHendelse") {
            logger.info { "Behandler PersonSynkroniseringHendelse: ${hendelse.referanseId}" }
            hentEllerOpprettPerson(hendelse.ident)
                .also { person ->
                    person.behandle(hendelse)
                    personRepository.oppdaterPerson(person)
                    arbeidssøkerMediator.behandle(person.ident)
                }
        }

    private fun behandleHendelse(hendelse: Hendelse) {
        try {
            personService
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

    fun overtaBekreftelse(ident: String) {
        logger.info { "Overta bekreftelse for ident" }
        personService
            .hentPerson(ident)
            ?.also { person ->
                if (person.observers.isEmpty()) {
                    personObservers.forEach { person.addObserver(it) }
                }
                person.overtaArbeidssøkerBekreftelse()
                personRepository.oppdaterPerson(person)
                logger.info { "Overtok bekreftelse" }
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
