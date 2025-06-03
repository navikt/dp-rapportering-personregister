package no.nav.dagpenger.rapportering.personregister.mediator.jobs

import io.ktor.client.HttpClient
import io.opentelemetry.instrumentation.annotations.WithSpan
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.connector.createHttpClient
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.modell.PersonObserver
import no.nav.dagpenger.rapportering.personregister.modell.Status
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
                        val identer = personRepository.hentPersonerUtenDagpengerMedAktivPeriode()
                        logger.info { "Hentet ${identer.size} identer for sending av på vegne av-melding" }
                        sendPaaVegneAv(identer, personRepository, observers)

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
        observers: List<PersonObserver>,
    ) {
        identer.forEach { ident ->
            val person =
                personRepository.hentPerson(ident)?.apply {
                    if (this.observers.isEmpty()) {
                        this.observers.addAll(observers)
                    }
                }

            if (person != null) {
                if (person.status == Status.IKKE_DAGPENGERBRUKER) {
                    sikkerLogg.info { "Sender frasigelsesmelding for person med ident: $ident" }
                    person.observers.forEach { observer ->
                        observer.sendFrasigelsesmelding(person)
                    }
                } else {
                    sikkerLogg.info { "Ingen på vegne av-melding sendt for person med ident: $ident, status: ${person.status}" }
                }
            } else {
                sikkerLogg.warn { "Fant ikke person med ident: $ident" }
            }
        }
    }
}
