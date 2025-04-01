package no.nav.dagpenger.rapportering.personregister.mediator.jobs

import io.ktor.client.HttpClient
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.connector.createHttpClient
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlin.concurrent.fixedRateTimer
import kotlin.time.Duration.Companion.days
import kotlin.time.measureTime

private val logger = KotlinLogging.logger {}

internal class SlettPersonerJob(
    private val httpClient: HttpClient = createHttpClient(),
) {
    private val tidspunktForKjoring = LocalTime.of(0, 1)
    private val nå = ZonedDateTime.now()
    private val tidspunktForNesteKjoring = nå.with(tidspunktForKjoring).plusDays(1)
    private val millisekunderTilNesteKjoring =
        tidspunktForNesteKjoring.toInstant().toEpochMilli() -
            nå.toInstant().toEpochMilli() // differansen i millisekunder mellom de to tidspunktene

    internal fun start(
        personRepository: PersonRepository,
    ) {
        logger.info { "Tidspunkt for neste kjøring SlettPersonerJob: $tidspunktForNesteKjoring" }
        fixedRateTimer(
            name = "Slett personer",
            daemon = true,
            initialDelay = millisekunderTilNesteKjoring.coerceAtLeast(0),
            period = 1.days.inWholeMilliseconds,
            action = {
                try {
                    if (isLeader(httpClient, logger)) {
                        logger.info { "Starter jobb for å slette personer" }
                        var antallPersoner: Int
                        val tidBrukt =
                            measureTime {
                                antallPersoner = slettPersoner(personRepository)
                            }
                        logger.info {
                            "Jobb for å slette personer ferdig. " +
                                "Slettet $antallPersoner på ${tidBrukt.inWholeSeconds} sekund(er)."
                        }
                    } else {
                        logger.info { "Pod er ikke leader, så jobb for å slette personer startes ikke" }
                    }
                } catch (e: Exception) {
                    logger.error(e) { "Jobb for å slette personer feilet" }
                }
            },
        )
    }

    fun slettPersoner(
        personRepository: PersonRepository,
    ): Int {
        val personer = personRepository.hentPersonerSomKanSlettes()
        personer.forEach {
            personRepository.slettPerson(it)
        }
        return personer.size
    }
}
