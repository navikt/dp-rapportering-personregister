package no.nav.dagpenger.rapportering.personregister.mediator.jobs

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.rapportering.personregister.mediator.MeldepliktMediator
import no.nav.dagpenger.rapportering.personregister.mediator.PersonMediator
import no.nav.dagpenger.rapportering.personregister.mediator.connector.MeldepliktConnector
import no.nav.dagpenger.rapportering.personregister.mediator.connector.createHttpClient
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.AnnenMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.DagpengerMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.MeldepliktHendelse
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.VedtakHendelse
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlin.concurrent.fixedRateTimer
import kotlin.time.Duration.Companion.days
import kotlin.time.measureTime

private val logger = KotlinLogging.logger {}
private val sikkerLogg = KotlinLogging.logger("tjenestekall")

internal class AktiverHendelserJob(
    private val httpClient: HttpClient = createHttpClient(),
) {
    private val tidspunktForKjoring = LocalTime.of(0, 1)
    private val nå = ZonedDateTime.now()
    private val tidspunktForNesteKjoring = nå.with(tidspunktForKjoring).plusDays(1)
    private val millisekunderTilNesteKjoring =
        tidspunktForNesteKjoring.toInstant().toEpochMilli() -
            nå.toInstant().toEpochMilli() // differansen i millisekunder mellom de to tidspunktene

    internal fun start(
        personRepository: PersonRepository,
        personMediator: PersonMediator,
        meldepliktMediator: MeldepliktMediator,
        meldepliktConnector: MeldepliktConnector,
    ) {
        logger.info { "Tidspunkt for neste kjøring AktiverHendelserJob: $tidspunktForNesteKjoring" }
        fixedRateTimer(
            name = "Aktiver hendelser med dato fram i tid",
            daemon = true,
            initialDelay = millisekunderTilNesteKjoring.coerceAtLeast(0),
            period = 1.days.inWholeMilliseconds,
            action = {
                try {
                    if (isLeader(httpClient, logger)) {
                        logger.info { "Starter jobb for å aktivere hendelser vi mottok med dato fram i tid" }

                        var antallHendelser: Int
                        val tidBrukt =
                            measureTime {
                                antallHendelser =
                                    aktivererHendelser(
                                        personRepository,
                                        personMediator,
                                        meldepliktMediator,
                                        meldepliktConnector,
                                    )
                            }

                        logger.info {
                            "Jobb for å aktivere hendelser vi mottok med dato fram i tid ferdig. " +
                                "Aktiverte $antallHendelser på ${tidBrukt.inWholeSeconds} sekund(er)."
                        }
                    } else {
                        logger.info { "Pod er ikke leader, så jobb for å aktivere fremtidige hendelser startes ikke" }
                    }
                } catch (e: Exception) {
                    logger.error(e) { "Jobb for å aktivere hendelser mottatt med dato fram i tid feilet" }
                }
            },
        )
    }

    fun aktivererHendelser(
        personRepository: PersonRepository,
        personMediator: PersonMediator,
        meldepliktMediator: MeldepliktMediator,
        meldepliktConnector: MeldepliktConnector,
    ): Int {
        val hendelser = personRepository.hentHendelserSomSkalAktiveres()
        var currentIdent = ""
        var currentStatus = false

        hendelser.forEach {
            // Sjekk om bruker har meldt seg
            // Hendelser er sortert etter ident
            // Da kan vi hente meldestatus for hver ident og ikke for hver hendelse
            if (it.ident != currentIdent) {
                val meldestatus =
                    runBlocking {
                        meldepliktConnector.hentMeldestatus(ident = it.ident)
                    }

                // Det er veldig rart at vi ikke kan hente meldestatus her fordi vi fikk data fra Arena, men for sikkerhetsskyld
                if (meldestatus == null) {
                    logger.error { "Kunne ikke hente meldestatus" }
                    sikkerLogg.error { "Kunne ikke hente meldestatus for person med ident ${it.ident}" }
                    throw RuntimeException("Kunne ikke hente meldestatus")
                }

                currentIdent = it.ident
                currentStatus = meldestatus.harMeldtSeg
            }

            when (it) {
                is DagpengerMeldegruppeHendelse -> personMediator.behandle(it.copy(harMeldtSeg = currentStatus))
                is AnnenMeldegruppeHendelse -> personMediator.behandle(it.copy(harMeldtSeg = currentStatus))
                is MeldepliktHendelse -> meldepliktMediator.behandle(it.copy(harMeldtSeg = currentStatus))
                is VedtakHendelse -> personMediator.behandle(it)
                else -> logger.warn { "Fant ukjent fremtidig hendelsetype $it" }
            }
            personRepository.slettFremtidigHendelse(it.referanseId)
        }
        return hendelser.size
    }
}
