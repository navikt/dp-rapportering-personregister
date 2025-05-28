package no.nav.dagpenger.rapportering.personregister.mediator.jobs

import io.ktor.client.HttpClient
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.connector.createHttpClient
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.service.PersonService
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlin.concurrent.fixedRateTimer
import kotlin.time.Duration.Companion.hours

private val logger = KotlinLogging.logger {}

internal class ResendPåVegneAvMelding(
    private val httpClient: HttpClient = createHttpClient(),
) {
    private val tidspunktForKjoring = LocalTime.now().plusMinutes(5)
    private val nå = ZonedDateTime.now()
    private val tidspunktForNesteKjoring = nå.with(tidspunktForKjoring).plusHours(1)
    private val millisekunderTilNesteKjoring =
        tidspunktForNesteKjoring.toInstant().toEpochMilli() -
            nå.toInstant().toEpochMilli() // differansen i millisekunder mellom de to tidspunktene

    fun start(
        personRepository: PersonRepository,
        personService: PersonService,
    ) {
        logger.info { "Tidspunkt for neste kjøring av ResendPåVegneAvMelding: $tidspunktForNesteKjoring" }
        fixedRateTimer(
            name = "Resend PaaVegneAv",
            daemon = true,
            initialDelay = millisekunderTilNesteKjoring.coerceAtLeast(0),
            period = 1.hours.inWholeMilliseconds,
            action = {
                try {
                    if (isLeader(httpClient, logger)) {
                        logger.info { "Starter jobb for å sende på vegne av-meldinger" }
                        val identer = personRepository.hentPersonerMedDagpenger()

                        logger.info {
                            "Jobb for å sende på vegne av-meldinger ferdig. Skal ha sendt overtagelse for ${identer.size} personer."
                        }
                    } else {
                        logger.info { "Pod er ikke leader, så jobb for å sende på vegne av-meldinger startes ikke her" }
                    }
                } catch (ex: Exception) {
                    logger.error(ex) { "Jobb for å sende på vegne av-meldinger feilet" }
                }
            },
        )
    }
}
