package no.nav.dagpenger.rapportering.personregister.mediator.jobs

import io.ktor.client.HttpClient
import io.opentelemetry.instrumentation.annotations.WithSpan
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.connector.createHttpClient
import no.nav.dagpenger.rapportering.personregister.mediator.db.OptimisticLockingException
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.db.TempPerson
import no.nav.dagpenger.rapportering.personregister.mediator.db.TempPersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.db.TempPersonStatus
import no.nav.dagpenger.rapportering.personregister.modell.PersonObserver
import no.nav.dagpenger.rapportering.personregister.modell.Status
import no.nav.dagpenger.rapportering.personregister.modell.gjeldende
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlin.concurrent.fixedRateTimer

private val logger = KotlinLogging.logger {}
private val sikkerLogg = KotlinLogging.logger("tjenestekall")

internal class SendPaaVegneAvForAlleJob(
    private val httpClient: HttpClient = createHttpClient(),
) {
    private val tidspunktForKjoring = LocalTime.now().plusMinutes(5)
    private val nå = ZonedDateTime.now()
    private val tidspunktForNesteKjoring = nå.with(tidspunktForKjoring)
    private val millisekunderTilNesteKjoring =
        tidspunktForNesteKjoring.toInstant().toEpochMilli() -
            nå.toInstant().toEpochMilli() // differansen i millisekunder mellom de to tidspunktene

    @WithSpan
    fun start(
        personRepository: PersonRepository,
        tempPersonRepository: TempPersonRepository,
        observers: List<PersonObserver>,
    ) {
        logger.info { "Tidspunkt for neste kjøring av SendPaaVegneAvForAlleJob: $tidspunktForNesteKjoring" }
        fixedRateTimer(
            name = "Send paaVegneAv-melding for alle",
            daemon = true,
            initialDelay = millisekunderTilNesteKjoring.coerceAtLeast(0),
            period = Long.MAX_VALUE,
            action = {
                try {
                    if (isLeader(httpClient, logger)) {
                        logger.info { "Starter jobb for å sende paavegneav-melding" }
                        val identer = hentTempPersonIdenter(tempPersonRepository)
                        logger.info { "Hentet ${identer.size} identer for sending av på vegne av-melding" }

                        sendPaaVegneAv(identer, personRepository, tempPersonRepository, observers)

                        logger.info { "Jobb for å sende paavegneav-melding er fullført" }
                    } else {
                        logger.info { "Pod er ikke leader, så jobb for å sende på vegne av-meldinger startes ikke her" }
                    }
                } catch (ex: Exception) {
                    logger.error(ex) { "Jobb for å sende på vegne av-meldinger feilet" }
                }
            },
        )
    }

    fun sendPaaVegneAv(
        identer: List<String>,
        personRepository: PersonRepository,
        tempPersonRepository: TempPersonRepository,
        observers: List<PersonObserver>,
    ) {
        identer.forEach { ident ->
            val tempPerson = tempPersonRepository.hentPerson(ident)
            if (tempPerson != null && tempPerson.status == TempPersonStatus.RETTET) {
                val person =
                    personRepository.hentPerson(ident)?.apply {
                        if (this.observers.isEmpty()) {
                            observers.forEach { observer ->
                                this.addObserver(observer)
                            }
                        }
                    }

                if (person != null) {
                    person.arbeidssøkerperioder.gjeldende?.overtattBekreftelse = null
                    try {
                        personRepository.oppdaterPerson(person)
                    } catch (e: OptimisticLockingException) {
                        sikkerLogg.error(e) { "Optimistisk låsing feilet ved oppdatering av person med ident: $ident" }
                    } catch (e: Exception) {
                        sikkerLogg.error(e) { "Feil ved oppdatering av person med ident: $ident" }
                    }

                    if (person.status == Status.DAGPENGERBRUKER) {
                        person.observers.forEach { observer ->
                            observer.sendOvertakelsesmelding(person)
                        }
                    } else {
                        person.observers.forEach { observer ->
                            observer.sendFrasigelsesmelding(person)
                        }
                    }
                    tempPersonRepository.oppdaterPerson(
                        TempPerson(ident, status = TempPersonStatus.FERDIGSTILT),
                    )
                } else {
                    sikkerLogg.warn { "Fant ikke person med ident: $ident" }
                }
            } else {
                sikkerLogg.warn {
                    "Fant ikke midlertidig person med ident: $ident eller status er ikke rettet: ${tempPerson?.status}"
                }
            }
        }
    }
}

private fun hentTempPersonIdenter(tempPersonRepository: TempPersonRepository): List<String> {
    try {
        logger.info { "Henter identer fra persontabell" }
        if (tempPersonRepository.isEmpty()) {
            logger.info { "Fyller midlertidig person tabell" }
            tempPersonRepository.syncPersoner()

            logger.info { "Henter midlertidig identer" }
            val identer = tempPersonRepository.hentAlleIdenter()
            logger.info { "Hentet ${identer.size} midlertidige identer" }

            try {
                identer.map { ident ->
                    try {
                        val tempPerson = TempPerson(ident)
                        logger.info { "Lagrer midlertidig person med ident: ${tempPerson.ident} og status: ${tempPerson.status}" }
                        tempPersonRepository.lagrePerson(tempPerson)
                        logger.info { "Lagring av midlertidig person med ident: ${tempPerson.ident} fullført" }
                    } catch (e: Exception) {
                        logger.error(e) { "Feil ved lagring av midlertidig person med ident: $ident" }
                    }
                }
                logger.info { "Midlertidig person tabell er fylt med ${identer.size}" }

                return tempPersonRepository.hentAlleIdenter()
            } catch (e: Exception) {
                logger.error(e) { "Feil ved lagring av midlertidige personer i databasen" }
            }
        }

        logger.info { "Midlertidig person tabell er allerede fylt." }
        return tempPersonRepository.hentAlleIdenter()
    } catch (e: Exception) {
        logger.error(e) { "Feil ved henting av midlertidig identer" }
        return emptyList()
    }
}
