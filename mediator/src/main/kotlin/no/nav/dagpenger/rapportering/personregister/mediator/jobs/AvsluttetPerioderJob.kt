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
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlin.concurrent.fixedRateTimer

private val logger = KotlinLogging.logger {}

internal class AvsluttetPerioderJob(
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
        logger.info { "Tidspunkt for neste kjøring av AvsluttetPerioderJob: $tidspunktForNesteKjoring" }
        fixedRateTimer(
            name = "Sjekk status avvik",
            daemon = true,
            initialDelay = millisekunderTilNesteKjoring.coerceAtLeast(0),
            period = Long.MAX_VALUE,
            action = {
                try {
                    if (isLeader(httpClient, logger)) {
                        logger.info { "Starter jobb for å opppdatere avsluttet perioder" }
                        val identer = hentTempPersonIdenter(tempPersonRepository)

                        logger.info { "Hentet ${identer.size} identer for sjekking av status" }

                        identer.forEach { ident ->
                            val tempPerson = tempPersonRepository.hentPerson(ident)
                            logger.info { "Hentet midlertidig person. Behandler" }
                            if (tempPerson != null && tempPerson.status == TempPersonStatus.IKKE_PABEGYNT) {
                                val person = personRepository.hentPerson(ident)

                                if (person != null) {
                                    val gjeldendePeriode =
                                        person
                                            ?.arbeidssøkerperioder
                                            ?.firstOrNull { it.avsluttet == null }

                                    if (gjeldendePeriode == null && person.status == Status.DAGPENGERBRUKER) {
                                        logger.info { "Person er Dagpengerbruker men har ingen gjeldende periode." }
                                        person.setStatus(Status.IKKE_DAGPENGERBRUKER)
                                        person.arbeidssøkerperioder.forEach { it.overtattBekreftelse = false }

                                        try {
                                            personRepository.oppdaterPerson(person)
                                        } catch (e: Exception) {
                                            logger.error(e) { "Feil ved oppdatering av person" }
                                        }
                                        try {
                                            tempPersonRepository.oppdaterPerson(TempPerson(ident, TempPersonStatus.RETTET))
                                        } catch (e: Exception) {
                                            logger.error(e) { "Feil ved oppdatering av personstatus til RETTET" }
                                        }
                                    } else {
                                        logger.info { "Person har en gjeldende periode. Ingen oppdatering nødvendig." }
                                        try {
                                            tempPersonRepository.oppdaterPerson(TempPerson(ident, TempPersonStatus.FERDIGSTILT))
                                        } catch (e: Exception) {
                                            logger.error(e) { "Feil ved oppdatering av personstatus til AVVIK for ident $ident" }
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
