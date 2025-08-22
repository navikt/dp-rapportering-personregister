package no.nav.dagpenger.rapportering.personregister.mediator.jobs.midlertidig

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.opentelemetry.instrumentation.annotations.WithSpan
import no.nav.dagpenger.rapportering.personregister.mediator.connector.createHttpClient
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.jobs.isLeader
import no.nav.dagpenger.rapportering.personregister.modell.PersonObserver
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlin.concurrent.fixedRateTimer
import kotlin.time.Duration.Companion.hours

private val logger = KotlinLogging.logger {}
private val sikkerLogg = KotlinLogging.logger("tjenestekall")

internal class AvvikPersonsynkroniseringJob(
    private val httpClient: HttpClient = createHttpClient(),
) {
    private val tidspunktForKjoring = LocalTime.now().plusMinutes(10)
    private val nå = ZonedDateTime.now()
    private val tidspunktForNesteKjoring = nå.with(tidspunktForKjoring)
    private val millisekunderTilNesteKjoring =
        tidspunktForNesteKjoring.toInstant().toEpochMilli() -
            nå.toInstant().toEpochMilli() // differansen i millisekunder mellom de to tidspunktene

    @WithSpan
    fun start(
        personRepository: PersonRepository,
        observerList: List<PersonObserver>,
    ) {
        logger.info { "Tidspunkt for neste kjøring av AvvikPersonsynkroniseringJob: $tidspunktForNesteKjoring" }
        fixedRateTimer(
            name = "Sjekk Personsynkronisering avvik",
            daemon = true,
            initialDelay = millisekunderTilNesteKjoring.coerceAtLeast(0),
            period = 7.hours.inWholeMilliseconds,
            action = {
                try {
                    if (isLeader(httpClient, logger)) {
                        logger.info { "Starter jobb for å sjekke personsynkronisering avvik" }
                        val identer = personRepository.hentAlleIdenter()

                        logger.info { "Hentet ${identer.size} identer for sjekking av personsynk-avvik" }

                        var antallAvvikPersoner = 0

                        identer.forEach { ident ->
                            val person =
                                personRepository.hentPerson(ident)?.apply {
                                    if (this.observers.isEmpty()) {
                                        observerList.forEach { observer -> this.addObserver(observer) }
                                    }
                                }

                            if (person != null) {
                                if (harPersonsynkroniseringAvvik(person)) {
                                    sikkerLogg.info { "Person har personsynk avvik: ident: $ident nåværende" }
                                    antallAvvikPersoner++
                                }
                            } else {
                                logger.warn { "Fant ikke person som vi skulle behandle" }
                            }
                        }

                        logger.info { "Jobb for å sjekke personsynkavvik er fullført. Antall avvik = $antallAvvikPersoner" }
                    } else {
                        logger.info { "Pod er ikke leader, så jobb for å oppdatere personsynkavvik startes ikke her" }
                    }
                } catch (ex: Exception) {
                    logger.error(ex) { "Jobb for å sjekke personsynkavvik feilet" }
                }
            },
        )
    }
}
