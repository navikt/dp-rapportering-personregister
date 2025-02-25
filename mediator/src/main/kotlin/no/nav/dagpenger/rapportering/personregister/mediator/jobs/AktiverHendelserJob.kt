package no.nav.dagpenger.rapportering.personregister.mediator.jobs

import com.fasterxml.jackson.annotation.JsonProperty
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.PersonstatusMediator
import no.nav.dagpenger.rapportering.personregister.mediator.connector.createHttpClient
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.modell.AnnenMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.DagpengerMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.MeldepliktHendelse
import java.net.InetAddress
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlin.concurrent.fixedRateTimer
import kotlin.time.Duration.Companion.days
import kotlin.time.measureTime

private val logger = KotlinLogging.logger {}

internal class AktiverHendelserJob(
    private val httpClient: HttpClient = createHttpClient(),
) {
    private val tidspunktForKjoring = LocalTime.of(0, 50)
    private val nå = ZonedDateTime.now()
    private val tidspunktForNesteKjoring = nå.with(tidspunktForKjoring).plusDays(1)
    private val millisekunderTilNesteKjoring =
        tidspunktForNesteKjoring.toInstant().toEpochMilli() -
            nå.toInstant().toEpochMilli() // differansen i millisekunder mellom de to tidspunktene

    internal fun start(
        personRepository: PersonRepository,
        personstatusMediator: PersonstatusMediator,
    ) {
        logger.info { "Tidspunkt for neste kjøring: $tidspunktForNesteKjoring" }
        fixedRateTimer(
            name = "Aktiver hendelser med dato fram i tid",
            daemon = true,
            initialDelay = millisekunderTilNesteKjoring.coerceAtLeast(0),
            period = 1.days.inWholeMilliseconds,
            action = {
                try {
                    if (isLeader()) {
                        logger.info { "Starter jobb for å aktivere hendelser vi mottok med dato fram i tid" }
                        var antallHendelser: Int
                        val tidBrukt =
                            measureTime {
                                val hendelser = personRepository.hentHendelserSomSkalAktiveres()
                                antallHendelser = hendelser.size
                                hendelser.forEach {
                                    when (it) {
                                        is DagpengerMeldegruppeHendelse -> personstatusMediator.behandle(it)
                                        is AnnenMeldegruppeHendelse -> personstatusMediator.behandle(it)
                                        is MeldepliktHendelse -> personstatusMediator.behandle(it)
                                        else -> logger.warn { "Fant ukjent fremtidig hendelsetype $it" }
                                    }
                                    personRepository.slettFremtidigHendelse(it.referanseId)
                                }
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

    private fun isLeader(): Boolean {
        var leader = ""
        val hostname = InetAddress.getLocalHost().hostName

        try {
            val electorUrl = System.getenv("ELECTOR_GET_URL")
            runBlocking {
                val leaderJson: Leader = httpClient.get(electorUrl).body()
                leader = leaderJson.name
            }
        } catch (e: Exception) {
            logger.error(e) { "Kunne ikke sjekke leader" }
            return true // Det er bedre å få flere pod'er til å starte jobben enn ingen
        }

        return hostname == leader
    }
}

data class Leader(
    val name: String,
    @JsonProperty("last_update")
    val lastUpdate: String,
)
