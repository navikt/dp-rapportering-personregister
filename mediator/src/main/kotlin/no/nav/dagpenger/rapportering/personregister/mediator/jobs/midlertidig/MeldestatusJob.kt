package no.nav.dagpenger.rapportering.personregister.mediator.jobs.midlertidig

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.opentelemetry.instrumentation.annotations.WithSpan
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.rapportering.personregister.mediator.MeldestatusMediator
import no.nav.dagpenger.rapportering.personregister.mediator.connector.MeldepliktConnector
import no.nav.dagpenger.rapportering.personregister.mediator.connector.createHttpClient
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.db.TempPerson
import no.nav.dagpenger.rapportering.personregister.mediator.db.TempPersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.db.TempPersonStatus
import no.nav.dagpenger.rapportering.personregister.mediator.jobs.isLeader
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.UUID
import kotlin.concurrent.fixedRateTimer
import kotlin.time.Duration.Companion.hours
import kotlin.time.measureTime

private val logger = KotlinLogging.logger {}
private val sikkerLogg = KotlinLogging.logger("tjenestekall")

internal class MeldestatusJob(
    private val httpClient: HttpClient = createHttpClient(),
) {
    private val tidspunktForKjoring = LocalTime.now().plusMinutes(2)
    private val nå = ZonedDateTime.now()
    private val tidspunktForNesteKjoring = nå.with(tidspunktForKjoring)
    private val millisekunderTilNesteKjoring =
        tidspunktForNesteKjoring.toInstant().toEpochMilli() -
            nå.toInstant().toEpochMilli() // differansen i millisekunder mellom de to tidspunktene

    @WithSpan
    internal fun start(
        personRepository: PersonRepository,
        tempPersonRepository: TempPersonRepository,
        meldepliktConnector: MeldepliktConnector,
        meldestatusMediator: MeldestatusMediator,
    ) {
        logger.info { "Tidspunkt for neste kjøring av MeldestatusJob: $tidspunktForNesteKjoring" }
        fixedRateTimer(
            name = "Sjekk meldestatus",
            daemon = true,
            initialDelay = millisekunderTilNesteKjoring.coerceAtLeast(0),
            period = 3.hours.inWholeMilliseconds,
            action = {
                try {
                    if (isLeader(httpClient, logger)) {
                        logger.info { "Starter jobb for å sjekke meldestatus" }

                        var antallPersoner: Int
                        val tidBrukt =
                            measureTime {
                                antallPersoner =
                                    runBlocking {
                                        oppdaterStatus(
                                            personRepository,
                                            tempPersonRepository,
                                            meldepliktConnector,
                                            meldestatusMediator,
                                        )
                                    }
                            }

                        logger.info {
                            "Jobb for å sjekke meldestatus er ferdig" +
                                "Sjekket $antallPersoner på ${tidBrukt.inWholeSeconds} sekund(er)."
                        }
                    } else {
                        logger.info { "Pod er ikke leader, så jobb for å oppdatere meldestatus startes ikke her" }
                    }
                } catch (ex: Exception) {
                    logger.error(ex) { "Jobb for å sjekke meldestatus feilet" }
                }
            },
        )
    }

    suspend fun oppdaterStatus(
        personRepository: PersonRepository,
        tempPersonRepository: TempPersonRepository,
        meldepliktConnector: MeldepliktConnector,
        meldestatusMediator: MeldestatusMediator,
    ): Int {
        // TODO: Husk å slette alt fra temp_person før første kjøring
        if (tempPersonRepository.isEmpty()) {
            tempPersonRepository.syncPersoner()
        }

        val identer = tempPersonRepository.hentAlleIdenterMedStatus(TempPersonStatus.IKKE_PABEGYNT)

        logger.info { "Hentet ${identer.size} identer for sjekking av meldestatus" }

        identer.forEach { ident ->
            val person = personRepository.hentPerson(ident)

            if (person != null) {
                try {
                    val meldestatus = meldepliktConnector.hentMeldestatus(ident = person.ident)

                    if (meldestatus == null) {
                        logger.info { "Person finnes ikke i Arena. Hopper over" }
                        sikkerLogg.info { "Person med ident ${person.ident} finnes ikke i Arena. Hopper over" }
                    } else {
                        meldestatusMediator.behandleHendelse(
                            UUID.randomUUID().toString(),
                            person,
                            meldestatus,
                        )
                    }

                    tempPersonRepository.oppdaterPerson(
                        TempPerson(
                            ident,
                            TempPersonStatus.FERDIGSTILT,
                        ),
                    )
                } catch (e: Exception) {
                    logger.error(e) { "Feil ved sjekking av meldestatus" }
                    sikkerLogg.error(e) { "Feil ved sjekking av meldestatus for person med ident $ident" }

                    tempPersonRepository.oppdaterPerson(
                        TempPerson(
                            ident,
                            TempPersonStatus.AVVIK,
                        ),
                    )
                }
            }
        }

        return identer.size
    }
}
