package no.nav.dagpenger.rapportering.personregister.mediator

import io.getunleash.Unleash
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.rapportering.personregister.mediator.db.OptimisticLockingException
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.ActionTimer
import no.nav.dagpenger.rapportering.personregister.mediator.service.PersonService
import no.nav.dagpenger.rapportering.personregister.modell.AnsvarligSystem
import no.nav.dagpenger.rapportering.personregister.modell.PersonObserver
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.AnnenMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.DagpengerMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.Hendelse
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.IkkeMeldtSegPå21DagerHendelse
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.NødbremsHendelse
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.PersonIkkeDagpengerSynkroniseringHendelse
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.PersonSynkroniseringHendelse
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.VedtakHendelse
import no.nav.dagpenger.rapportering.personregister.modell.utils.erIFortid

class PersonMediator(
    private val personRepository: PersonRepository,
    private val personService: PersonService,
    private val arbeidssøkerMediator: ArbeidssøkerMediator,
    private val personObservers: List<PersonObserver>,
    private val meldepliktMediator: MeldepliktMediator,
    private val actionTimer: ActionTimer,
) {
    fun behandle(
        vedtakHendelse: VedtakHendelse,
        counter: Int = 1,
    ): Unit =
        actionTimer.timedAction("behandle_VedtakHendelse") {
            logger.info { "Behandler vedtakshendelse: ${vedtakHendelse.referanseId}" }
            personService
                .hentEllerOpprettPerson(vedtakHendelse.ident)
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
            if (hendelse.sluttDato.erIFortid()) {
                logger.info { "DagpengerMeldegruppeHendelse med referanseId ${hendelse.referanseId} gjelder tilbake i tid. Ignorerer." }
            } else {
                personService
                    .hentEllerOpprettPerson(hendelse.ident)
                    .also { person ->
                        if (person.ansvarligSystem == AnsvarligSystem.ARENA) {
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
                                runBlocking { meldepliktMediator.behandle(hendelse.ident, hendelse.harMeldtSeg, hendelse.korrelasjonsId) }
                            }
                            arbeidssøkerMediator.behandle(hendelse.ident, hendelse.korrelasjonsId)
                        } else {
                            logger.info { "Behandler ikke DagpengerMeldegruppeHendelse, fordi Arena ikke er ansvarlig system" }
                        }
                    }
            }
        }

    fun behandle(hendelse: AnnenMeldegruppeHendelse) =
        actionTimer.timedAction("behandle_AnnenMeldegruppeHendelse") {
            logger.info { "Behandler annen meldegruppe hendelse: ${hendelse.referanseId}" }
            if (hendelse.sluttDato.erIFortid()) {
                logger.info { "AnnenMeldegruppeHendelse med referanseId ${hendelse.referanseId} gjelder tilbake i tid. Ignorerer." }
            } else {
                personService
                    .hentPerson(hendelse.ident)
                    ?.let {
                        if (it.ansvarligSystem == AnsvarligSystem.ARENA) {
                            behandleHendelse(hendelse)
                        } else {
                            logger.info { "Behandler ikke AnnenMeldegruppeHendelse, fordi Arena ikke er ansvarlig system" }
                        }
                    }
            }
        }

    suspend fun behandle(
        hendelse: PersonSynkroniseringHendelse,
        counter: Int = 1,
    ): Unit =
        actionTimer.coTimedAction("behandle_PersonSynkroniseringHendelse") {
            logger.info { "Behandler PersonSynkroniseringHendelse: ${hendelse.referanseId}" }
            personService
                .hentEllerOpprettPerson(hendelse.ident)
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
                    arbeidssøkerMediator.behandle(person.ident, hendelse.korrelasjonsId)
                }
        }

    suspend fun behandle(
        hendelse: PersonIkkeDagpengerSynkroniseringHendelse,
        counter: Int = 1,
    ): Unit =
        actionTimer.coTimedAction("behandle_PersonIkkeDagpengerSynkroniseringHendelse") {
            logger.info { "Behandler PersonIkkeDagpengerSynkroniseringHendelse: ${hendelse.referanseId}" }
            personService
                .hentEllerOpprettPerson(hendelse.ident)
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
                    arbeidssøkerMediator.behandle(person.ident, hendelse.korrelasjonsId)
                }
        }

    fun behandle(
        hendelse: IkkeMeldtSegPå21DagerHendelse,
        counter: Int = 1,
    ): Unit =
        actionTimer.timedAction("behandle_IkkeMeldtSegPå21DagerHendelse") {
            logger.info { "Behandler IkkeMeldtSegPå21DagerHendelse: ${hendelse.referanseId}" }
            personService
                .hentEllerOpprettPerson(hendelse.ident)
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

    fun behandle(nødbremsHendelse: NødbremsHendelse) {
        logger.info { "Behandler nødbrems hendelse: ${nødbremsHendelse.referanseId}" }
        behandleHendelse(nødbremsHendelse)
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
            logger.error(e) { "Feil ved behandling av hendelse: ${hendelse.referanseId}" }
            throw e
        }
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
