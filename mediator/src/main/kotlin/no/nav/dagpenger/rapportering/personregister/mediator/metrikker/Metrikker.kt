package no.nav.dagpenger.rapportering.personregister.mediator.metrikker

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.modell.Status
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.fixedRateTimer
import kotlin.random.Random
import kotlin.time.Duration.Companion.minutes
import kotlin.time.measureTime

private val logger = KotlinLogging.logger {}
private const val NAMESPACE = "dp_rapportering_personregister"

class SoknadMetrikker(
    meterRegistry: MeterRegistry,
) {
    val soknaderMottatt: Counter =
        Counter
            .builder("${NAMESPACE}_soknad_mottatt_total")
            .description("Indikerer antall mottatte søknader")
            .register(meterRegistry)
}

class VedtakMetrikker(
    private val meterRegistry: MeterRegistry,
) {
    fun vedtakMottatt(status: Status) =
        Counter
            .builder("${NAMESPACE}_vedtak_mottatt_total")
            .description("Indikerer antall mottatte vedtak")
            .tag("status", status.name)
            .register(meterRegistry)
}

class DatabaseMetrikker(
    meterRegistry: MeterRegistry,
) {
    private val lagredePersoner: AtomicInteger = AtomicInteger(0)
    private val lagredeHendelser: AtomicInteger = AtomicInteger(0)

    init {
        Gauge
            .builder("${NAMESPACE}_lagrede_personer_total", lagredePersoner) { it.get().toDouble() }
            .description("Antall lagrede personer i databasen")
            .register(meterRegistry)
        Gauge
            .builder("${NAMESPACE}_lagrede_hendelser_total", lagredeHendelser) { it.get().toDouble() }
            .description("Antall lagrede hendelser i databasen")
            .register(meterRegistry)
    }

    internal fun startRapporteringJobb(personRepository: PersonRepository) {
        fixedRateTimer(
            name = "Fast rapportering av lagrede elementer i databasen",
            daemon = true,
            initialDelay = Random.nextInt(5).minutes.inWholeMilliseconds,
            period = 10.minutes.inWholeMilliseconds,
            action = {
                try {
                    logger.info { "Oppdaterer metrikker for lagrede elementer i databasen" }
                    val antallLagredePersoner = personRepository.hentAnallPersoner()
                    val antallLagredeHendelser = personRepository.hentAntallHendelser()
                    lagredePersoner.set(antallLagredePersoner)
                    lagredeHendelser.set(antallLagredeHendelser)

                    logger.info { "Oppdaterte metrikker med lagrede personer $lagredePersoner, lagrede hendelser $lagredeHendelser" }
                } catch (e: Exception) {
                    logger.warn(e) { "Uthenting av metrikker for lagrede elementer i databasen feilet" }
                    lagredePersoner.set(-1)
                    lagredeHendelser.set(-1)
                }
            },
        )
    }
}

class ActionTimer(
    private val meterRegistry: MeterRegistry,
) {
    fun <T> timedAction(
        navn: String,
        block: () -> T,
    ): T {
        val blockResult: T
        val tidBrukt =
            measureTime {
                blockResult = block()
            }
        Timer
            .builder("${NAMESPACE}_timer")
            .tag("navn", navn)
            .description("Indikerer hvor lang tid en funksjon brukte")
            .register(meterRegistry)
            .record(tidBrukt.inWholeMilliseconds, MILLISECONDS)

        return blockResult
    }
}
