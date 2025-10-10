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
import no.nav.dagpenger.rapportering.personregister.mediator.service.PersonService
import no.nav.dagpenger.rapportering.personregister.modell.AnsvarligSystem
import no.nav.dagpenger.rapportering.personregister.modell.Status
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.UUID
import kotlin.concurrent.fixedRateTimer
import kotlin.time.Duration.Companion.days
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
        personService: PersonService,
        tempPersonRepository: TempPersonRepository,
        meldepliktConnector: MeldepliktConnector,
        meldestatusMediator: MeldestatusMediator,
    ) {
        logger.info { "Tidspunkt for neste kjøring av MeldestatusJob: $tidspunktForNesteKjoring" }
        fixedRateTimer(
            name = "Sjekk meldestatus",
            daemon = true,
            initialDelay = millisekunderTilNesteKjoring.coerceAtLeast(0),
            period = 7.days.inWholeMilliseconds,
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
                                            personService,
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
        personService: PersonService,
        tempPersonRepository: TempPersonRepository,
        meldepliktConnector: MeldepliktConnector,
        meldestatusMediator: MeldestatusMediator,
    ): Int {
        if (tempPersonRepository.isEmpty()) {
            tempPersonRepository.syncPersoner()
        }

        var antallPersoner = 0

        var identer = tempPersonRepository.hentIdenterMedStatus(TempPersonStatus.IKKE_PABEGYNT)
        while (identer.isNotEmpty()) {
            logger.info { "Hentet ${identer.size} identer for sjekking av meldestatus" }
            antallPersoner += identer.size

            identer.forEach { ident ->
                val person = personService.hentPerson(ident)

                if (person != null) {
                    if (person.ansvarligSystem == AnsvarligSystem.ARENA) {
                        try {
                            val meldestatus = meldepliktConnector.hentMeldestatus(ident = person.ident)

                            if (meldestatus == null) {
                                if (person.status == Status.DAGPENGERBRUKER) {
                                    logger.error { "Person finnes ikke i Arena, men har status DAGPENGERBRUKER" }
                                    sikkerLogg.error {
                                        "Person med ident ${person.ident} finnes ikke i Arena, men har status DAGPENGERBRUKER"
                                    }
                                    throw RuntimeException("Person finnes ikke i Arena, men har status DAGPENGERBRUKER")
                                } else {
                                    logger.info { "Person finnes ikke i Arena. Hopper over" }
                                    sikkerLogg.info { "Person med ident ${person.ident} finnes ikke i Arena. Hopper over" }
                                }
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
                    } else {
                        logger.info { "Person har ikke Arena som ansvarlig system. Henter ikke meldepliktendringer." }
                        sikkerLogg.info {
                            "Person med ident ${person.ident} har ikke Arena som ansvarlig system. Henter ikke meldepliktendringer."
                        }

                        tempPersonRepository.oppdaterPerson(
                            TempPerson(
                                ident,
                                TempPersonStatus.FERDIGSTILT,
                            ),
                        )
                    }
                }
            }

            identer = tempPersonRepository.hentIdenterMedStatus(TempPersonStatus.IKKE_PABEGYNT)
        }

        return antallPersoner
    }
}
