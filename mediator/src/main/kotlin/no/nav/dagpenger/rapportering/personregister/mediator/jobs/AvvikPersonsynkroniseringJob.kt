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

private val logger = KotlinLogging.logger {}

internal class AvvikPersonsynkroniseringJob(
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
        observerList: List<PersonObserver>,
    ) {
        logger.info { "Tidspunkt for neste kjøring av AvvikPersonsynkroniseringJob: $tidspunktForNesteKjoring" }
        fixedRateTimer(
            name = "Sjekk Personsynkronisering avvik",
            daemon = true,
            initialDelay = millisekunderTilNesteKjoring.coerceAtLeast(0),
            period = Long.MAX_VALUE,
            action = {
                try {
                    if (isLeader(httpClient, logger)) {
                        logger.info { "Starter jobb for å sjekke personsynkronisering avvik" }
                        val identer = hentTempPersonIdenter(tempPersonRepository)

                        logger.info { "Hentet ${identer.size} identer for sjekking av status" }

                        identer.forEach { ident ->
                            val tempPerson = tempPersonRepository.hentPerson(ident)
                            if (tempPerson != null && tempPerson.status == TempPersonStatus.IKKE_PABEGYNT) {
                                val person =
                                    personRepository.hentPerson(ident)?.apply {
                                        if (this.observers.isEmpty()) {
                                            observerList.forEach { observer -> this.addObserver(observer) }
                                        }
                                    }

                                if (person != null) {
                                    if (harPersonsynkroniseringAvvik(person)) {
                                        logger.info { "Person har personsynk avvik: nåværende meldegruppe: ${person.meldegruppe}" }
                                        try {
                                            personRepository.oppdaterPerson(person)
                                        } catch (ex: Exception) {
                                            logger.error(ex) { "Kunne ikke lagre person med oppdatert status" }
                                        }

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

                        logger.info { "Jobb for å sjekke personsynkavvik er fullført. Antall rettede personer" }
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
