package no.nav.dagpenger.rapportering.personregister.mediator.jobs.midlertidig

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import no.nav.dagpenger.rapportering.personregister.mediator.connector.createHttpClient
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.jobs.isLeader
import no.nav.dagpenger.rapportering.personregister.mediator.service.PersonService
import no.nav.dagpenger.rapportering.personregister.modell.Status
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlin.concurrent.fixedRateTimer
import kotlin.time.Duration.Companion.hours

private val logger = KotlinLogging.logger {}
private val sikkerLogg = KotlinLogging.logger("tjenestekall")

internal class ResendPåVegneAvMelding(
    private val httpClient: HttpClient = createHttpClient(),
) {
    private val tidspunktForKjoring = LocalTime.now().plusMinutes(40)
    private val nå = ZonedDateTime.now()
    private val tidspunktForNesteKjoring = nå.with(tidspunktForKjoring)
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
            period = 5.hours.inWholeMilliseconds,
            action = {
                try {
                    if (isLeader(httpClient, logger)) {
                        logger.info { "Starter jobb for å sende på vegne av-meldinger" }

                        val identer =
                            personRepository
                                .hentPersonerMedDagpengerMedAvvikBekreftelse()
                                .plus(personRepository.hentPersonerUtenDagpengerMedAvvikBekreftelse())
                                .distinct()

                        logger.info { "Hentet ${identer.size} identer som skal rettes" }

                        var antallOvertakelser = 0
                        var antallFrasigelser = 0

                        try {
                            identer.forEach { ident ->
                                val person = personService.hentPerson(ident)
                                if (person != null) {
                                    if (person.status == Status.DAGPENGERBRUKER) {
                                        sikkerLogg.info { "Sender overtakelsesmelding for ident: $ident" }
                                        person.observers.forEach { observer -> observer.sendOvertakelsesmelding(person) }
                                        antallOvertakelser++
                                    }

                                    if (person.status == Status.IKKE_DAGPENGERBRUKER) {
                                        sikkerLogg.info { "Sender frasigelsesmelding for ident: $ident" }
                                        person.observers.forEach { observer -> observer.sendFrasigelsesmelding(person) }
                                        antallFrasigelser++
                                    }
                                } else {
                                    sikkerLogg.warn { "Fant ikke person for ident: $ident" }
                                }
                            }
                        } catch (ex: Exception) {
                            logger.error(ex) { "Feil under sending av på vegne av-meldinger" }
                        }

                        logger.info {
                            "Jobb for å sende på vegne av-meldinger ferdig. Antall overtakelser: $antallOvertakelser, Antall frasigelser: $antallFrasigelser"
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
