package no.nav.dagpenger.rapportering.personregister.mediator.jobs

import io.ktor.client.HttpClient
import io.opentelemetry.instrumentation.annotations.WithSpan
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.connector.createHttpClient
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.db.TempPerson
import no.nav.dagpenger.rapportering.personregister.mediator.db.TempPersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.db.TempPersonStatus
import no.nav.dagpenger.rapportering.personregister.modell.Status
import no.nav.dagpenger.rapportering.personregister.modell.erArbeidssøker
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlin.concurrent.fixedRateTimer

private val logger = KotlinLogging.logger {}
private val sikkerLogg = KotlinLogging.logger("tjenestekall")

internal class AvvikStatusJob(
    private val httpClient: HttpClient = createHttpClient(),
) {
    private val tidspunktForKjoring = LocalTime.now().plusMinutes(2)
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

                                if (person != null && person.status == Status.DAGPENGERBRUKER) {
                                    val nyStatus = beregnStatus(person)
                                    if (nyStatus != person.status) {
                                        val beregnetMeldepliktStatus = beregnMeldepliktStatus(person)
                                        val beregnetMG = beregnMeldegruppeStatus(person)
                                        val erArbeidssøker = person.erArbeidssøker

                                        sikkerLogg.info {
                                            "Avvik: Person med ident: $ident har statusavvik. " +
                                                "Nåværende status: ${person.status}, " +
                                                "Beregnet status: $nyStatus, " +
                                                "Beregnet meldeplikt: $beregnetMeldepliktStatus, " +
                                                "Beregnet meldegruppe: $beregnetMG, " +
                                                "Er arbeidssøker: $erArbeidssøker"
                                        }
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
}
