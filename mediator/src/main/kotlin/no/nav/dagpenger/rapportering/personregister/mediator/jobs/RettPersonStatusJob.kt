package no.nav.dagpenger.rapportering.personregister.mediator.jobs

import io.ktor.client.HttpClient
import io.opentelemetry.instrumentation.annotations.WithSpan
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.connector.createHttpClient
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.db.TempPersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.db.TempPersonStatus
import no.nav.dagpenger.rapportering.personregister.mediator.service.ArbeidssøkerService
import no.nav.dagpenger.rapportering.personregister.modell.gjeldende
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlin.concurrent.fixedRateTimer
import kotlin.time.Duration.Companion.hours

private val logger = KotlinLogging.logger {}
private val sikkerLogg = KotlinLogging.logger("tjenestekall")

internal class RettPersonStatusJob(
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
        arbeidssøkerService: ArbeidssøkerService,
    ) {
        logger.info { "Tidspunkt for neste kjøring av RettPersonStatusJob: $tidspunktForNesteKjoring" }
        fixedRateTimer(
            name = "Rett person status",
            daemon = true,
            initialDelay = millisekunderTilNesteKjoring.coerceAtLeast(0),
            period = 3.hours.inWholeMilliseconds,
            action = {
                try {
                    if (isLeader(httpClient, logger)) {
                        logger.info { "Starter jobb for å oppdatere personstatus" }
                        val identer = hentTempPersonIdenter(tempPersonRepository)
                        logger.info { "Hentet ${identer.size} identer for oppdatering av personstatus" }

                        identer.forEach { ident ->
                            val tempPerson = tempPersonRepository.hentPerson(ident)
                            logger.info { "Hentet midlertidig person. Behandler" }
                            if (tempPerson != null && tempPerson.status == TempPersonStatus.IKKE_PABEGYNT) {
                                val person = personRepository.hentPerson(ident)

                                val sisteArbeidssøkerperiode =
                                    try {
                                        if (person?.arbeidssøkerperioder?.gjeldende != null) {
                                            logger.info { "Person har gjeldende periode. Bruker denne." }
                                            person.arbeidssøkerperioder.gjeldende
                                        } else {
                                            logger.info { "Person har ikke gjeldende periode. Henter siste arbeidssøkerperiode" }
                                            runBlocking { arbeidssøkerService.hentSisteArbeidssøkerperiode(ident) }
                                        }
                                    } catch (e: Exception) {
                                        sikkerLogg.error(e) { "Feil ved henting av siste arbeidssøkerperiode for person: $ident" }
                                        null
                                    }

                                if (person != null) {
//                                    val oppdatertPerson = rettPersonStatus(person, sisteArbeidssøkerperiode)
//                                    personRepository.oppdaterPerson(oppdatertPerson)
//                                    tempPersonRepository.oppdaterPerson(
//                                        TempPerson(ident, status = TempPersonStatus.RETTET),
//                                    )
                                } else {
                                    sikkerLogg.warn { "Fant ikke person med ident: $ident " }
                                }
                            }
                        }

                        logger.info { "Jobb for å oppdatere personstatus er fullført" }
                    } else {
                        logger.info { "Pod er ikke leader, så jobb for å oppdatere personstatus startes ikke her" }
                    }
                } catch (ex: Exception) {
                    logger.error(ex) { "Jobb for å oppdatere personstatus feilet" }
                }
            },
        )
    }
}
