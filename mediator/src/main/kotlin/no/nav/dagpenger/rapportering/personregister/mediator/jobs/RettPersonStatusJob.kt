package no.nav.dagpenger.rapportering.personregister.mediator.jobs

import io.ktor.client.HttpClient
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.connector.createHttpClient
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.db.TempPerson
import no.nav.dagpenger.rapportering.personregister.mediator.db.TempPersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.db.TempPersonStatus
import no.nav.dagpenger.rapportering.personregister.mediator.service.ArbeidssøkerService
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlin.concurrent.fixedRateTimer
import kotlin.time.Duration.Companion.hours

private val logger = KotlinLogging.logger {}

internal class RettPersonStatusJob(
    private val httpClient: HttpClient = createHttpClient(),
) {
    private val tidspunktForKjoring = LocalTime.now().plusMinutes(5)
    private val nå = ZonedDateTime.now()
    private val tidspunktForNesteKjoring = nå.with(tidspunktForKjoring)
    private val millisekunderTilNesteKjoring =
        tidspunktForNesteKjoring.toInstant().toEpochMilli() -
            nå.toInstant().toEpochMilli() // differansen i millisekunder mellom de to tidspunktene

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
            period = 1.hours.inWholeMilliseconds,
            action = {
                try {
                    if (isLeader(httpClient, logger)) {
                        logger.info { "Starter jobb for å oppdatere personstatus" }
                        val identer = hentTempPersonIdenter(personRepository, tempPersonRepository)
                        logger.info { "Hentet ${identer.size} identer for oppdatering av personstatus" }

                        identer.forEach { ident ->
                            val tempPerson = tempPersonRepository.hentPerson(ident)
                            if (tempPerson != null && tempPerson.status == TempPersonStatus.IKKE_PABEGYNT) {
                                val person = personRepository.hentPerson(ident)

                                val sisteArbeidssøkerperiode = runBlocking { arbeidssøkerService.hentSisteArbeidssøkerperiode(ident) }

                                if (person != null) {
                                    val oppdatertPerson = rettPersonStatus(person, sisteArbeidssøkerperiode)
                                    personRepository.oppdaterPerson(oppdatertPerson)
                                    tempPersonRepository.oppdaterPerson(
                                        TempPerson(ident, status = TempPersonStatus.FERDIGSTILT),
                                    )
                                }
                            }
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

private fun hentTempPersonIdenter(
    personRepository: PersonRepository,
    tempPersonRepository: TempPersonRepository,
): List<String> {
    logger.info { "Henter identer fra persontabell" }
    val identer = personRepository.hentAlleIdenter()
    logger.info { "Hentet ${identer.size} identer fra persontabell" }
    if (tempPersonRepository.isEmpty()) {
        logger.info { "Fyller midlertidig person tabell med ${identer.size} identer" }

        try {
            identer.map { ident ->
                val tempPerson = TempPerson(ident)
                logger.info { "Lagrer midlertidig person med ident: ${tempPerson.ident} og status: ${tempPerson.status}" }
                tempPersonRepository.lagrePerson(tempPerson)
                logger.info { "Lagring av midlertidig person med ident: ${tempPerson.ident} fullført" }
            }
            logger.info { "Midlertidig person tabell er fylt med ${identer.size}" }

            return tempPersonRepository.hentAlleIdenter()
        } catch (e: Exception) {
            logger.error(e) { "Feil ved lagring av midlertidige personer i databasen" }
        }
    }

    logger.info { "Midlertidig person tabell er allerede fylt." }
    return tempPersonRepository.hentAlleIdenter()
}
