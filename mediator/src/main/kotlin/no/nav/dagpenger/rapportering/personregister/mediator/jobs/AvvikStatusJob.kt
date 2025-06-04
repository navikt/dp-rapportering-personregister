package no.nav.dagpenger.rapportering.personregister.mediator.jobs

import io.ktor.client.HttpClient
import io.opentelemetry.instrumentation.annotations.WithSpan
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.connector.createHttpClient
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.db.TempPerson
import no.nav.dagpenger.rapportering.personregister.mediator.db.TempPersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.db.TempPersonStatus
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlin.concurrent.fixedRateTimer

private val logger = KotlinLogging.logger {}

internal class AvvikStatusJob(
    private val httpClient: HttpClient = createHttpClient(),
) {
    private val tidspunktForKjoring = LocalTime.now().plusMinutes(3)
    private val nå = ZonedDateTime.now()
    private val tidspunktForNesteKjoring = nå.with(tidspunktForKjoring)
    private val millisekunderTilNesteKjoring =
        tidspunktForNesteKjoring.toInstant().toEpochMilli() -
            nå.toInstant().toEpochMilli() // differansen i millisekunder mellom de to tidspunktene

    @WithSpan
    fun start(
        personRepository: PersonRepository,
        tempPersonRepository: TempPersonRepository,
    ) {
        logger.info { "Tidspunkt for neste kjøring av AvvikStatusJob: $tidspunktForNesteKjoring" }
        fixedRateTimer(
            name = "Sjekk status avvik",
            daemon = true,
            initialDelay = millisekunderTilNesteKjoring.coerceAtLeast(0),
            period = Long.MAX_VALUE,
            action = {
                try {
                    if (isLeader(httpClient, logger)) {
                        logger.info { "Starter jobb for å oppdatere personstatus" }
                        val identer = hentTempPersonIdenter(tempPersonRepository)

                        logger.info { "Hentet ${identer.size} identer for sjekking av status" }

                        identer.forEach { ident ->
                            val tempPerson = tempPersonRepository.hentPerson(ident)
                            logger.info { "Hentet midlertidig person. Behandler" }
                            if (tempPerson != null && tempPerson.status == TempPersonStatus.IKKE_PABEGYNT) {
                                val person = personRepository.hentPerson(ident)

                                if (person != null) {
                                    val nyStatus = beregnStatus(person)
                                    if (nyStatus != person.status) {
                                        logger.info { "Person har statusavvik: nåværende status: ${person.status}, beregnet: $nyStatus" }
                                        try {
                                            tempPersonRepository.oppdaterPerson(
                                                TempPerson(
                                                    ident = ident,
                                                    status = TempPersonStatus.AVVIK,
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

                        logger.info { "Jobb for å sjekke statusavvik er fullført" }
                    } else {
                        logger.info { "Pod er ikke leader, så jobb for å oppdatere personstatus startes ikke her" }
                    }
                } catch (ex: Exception) {
                    logger.error(ex) { "Jobb for å sjekke statusavvik feilet" }
                }
            },
        )
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
}
