package no.nav.dagpenger.rapportering.personregister.mediator.jobs

import io.ktor.client.HttpClient
import io.opentelemetry.instrumentation.annotations.WithSpan
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.connector.createHttpClient
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.db.TempPerson
import no.nav.dagpenger.rapportering.personregister.mediator.db.TempPersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.db.TempPersonStatus
import no.nav.dagpenger.rapportering.personregister.modell.PersonObserver
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlin.concurrent.fixedRateTimer
import kotlin.time.Duration.Companion.hours

private val logger = KotlinLogging.logger {}

internal class AvvikStatusJob(
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
        observerList: List<PersonObserver>,
    ) {
        logger.info { "Tidspunkt for neste kjøring av AvvikStatusJob: $tidspunktForNesteKjoring" }
        fixedRateTimer(
            name = "Sjekk status avvik",
            daemon = true,
            initialDelay = millisekunderTilNesteKjoring.coerceAtLeast(0),
            period = 3.hours.inWholeMilliseconds,
            action = {
                try {
                    if (isLeader(httpClient, logger)) {
                        logger.info { "Starter jobb for å oppdatere personstatus" }
                        val identer = hentTempPersonIdenter(tempPersonRepository)
                        var antallrettedePersoner = 0

                        logger.info { "Hentet ${identer.size} identer for sjekking av status" }

                        identer.forEach { ident ->
                            val tempPerson = tempPersonRepository.hentPerson(ident)
                            logger.info { "Hentet midlertidig person. Behandler" }
                            if (tempPerson != null && tempPerson.status == TempPersonStatus.IKKE_PABEGYNT) {
                                val person =
                                    personRepository.hentPerson(ident)?.apply {
                                        if (this.observers.isEmpty()) {
                                            observerList.forEach { observer -> this.addObserver(observer) }
                                        }
                                    }

                                if (person != null) {
                                    val nyStatus = beregnStatus(person)
                                    if (nyStatus != person.status) {
                                        logger.info { "Person har statusavvik: nåværende status: ${person.status}, beregnet: $nyStatus" }
                                        rettAvvik(person, nyStatus)
                                        antallrettedePersoner++
                                        try {
                                            personRepository.oppdaterPerson(person)
                                        } catch (ex: Exception) {
                                            logger.error(ex) { "Kunne ikke lagre person med oppdatert status" }
                                        }

                                        try {
                                            tempPersonRepository.oppdaterPerson(
                                                TempPerson(
                                                    ident = ident,
                                                    status = TempPersonStatus.RETTET,
                                                ),
                                            )
                                        } catch (ex: Exception) {
                                            logger.error(ex) { "Kunne ikke lagre midlertidig person" }
                                        }
                                    } else {
                                        logger.info { "Person har ingen statusavvik" }
                                        try {
                                            tempPersonRepository.oppdaterPerson(
                                                TempPerson(
                                                    ident = ident,
                                                    status = TempPersonStatus.FERDIGSTILT,
                                                ),
                                            )
                                        } catch (ex: Exception) {
                                            logger.error(ex) { "Kunne ikke lagre midlertidig person som er ferdigstilt" }
                                        }
                                    }
                                } else {
                                    logger.warn { "Fant ikke person som vi skulle behandle" }
                                }
                            }
                        }

                        logger.info { "Jobb for å sjekke personstatus er fullført. Antall rettede personer: $antallrettedePersoner" }
                    } else {
                        logger.info { "Pod er ikke leader, så jobb for å oppdatere personstatus startes ikke her" }
                    }
                } catch (ex: Exception) {
                    logger.error(ex) { "Jobb for å sjekke statusavvik feilet" }
                }
            },
        )
    }
}
