package no.nav.dagpenger.rapportering.personregister.mediator.metrikker

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.fixedRateTimer
import kotlin.random.Random
import kotlin.time.Duration.Companion.minutes
import kotlin.time.measureTime

private val logger = KotlinLogging.logger {}

private const val NAMESPACE = "dp_rapportering_personregister"
private const val ACTION_TIMER_DESCRIPTION = "Indikerer hvor lang tid en funksjon brukte"

class SøknadMetrikker(
    meterRegistry: MeterRegistry,
) {
    val søknaderMottatt: Counter =
        Counter
            .builder("${NAMESPACE}_soknad_mottatt_total")
            .description("Antall mottatte meldinger om mottatte søknader")
            .register(meterRegistry)
    val søknaderFeilet: Counter =
        Counter
            .builder("${NAMESPACE}_soknad_feilet_total")
            .description("Antall behandlinger som har feilet av mottatte meldinger om mottatte søknader")
            .register(meterRegistry)
}

class BehandlingsresultatMetrikker(
    meterRegistry: MeterRegistry,
) {
    val behandlingsresultatMottatt: Counter =
        Counter
            .builder("${NAMESPACE}_behandlingsresultat_mottatt_total")
            .description("Antall mottatte meldinger om behandlingsresultater")
            .register(meterRegistry)
    val behandlingsresultatFeilet: Counter =
        Counter
            .builder("${NAMESPACE}_behandlingsresultat_feilet_total")
            .description("Antall behandlinger som har feilet av mottatte meldinger om behandlingsresultater")
            .register(meterRegistry)
}

class MeldestatusMetrikker(
    meterRegistry: MeterRegistry,
) {
    val meldestatusMottatt: Counter =
        Counter
            .builder("${NAMESPACE}_meldestatus_mottatt_total")
            .description("Antall mottatte meldinger om meldestatus fra Arena")
            .register(meterRegistry)
    val meldestatusFeilet: Counter =
        Counter
            .builder("${NAMESPACE}_meldestatus_feilet_total")
            .description("Antall behandlinger som har feilet av mottatte meldinger om meldestatus fra Arena")
            .register(meterRegistry)
}

class MeldegruppeendringMetrikker(
    meterRegistry: MeterRegistry,
) {
    val dagpengerMeldegruppeMottatt: Counter =
        Counter
            .builder("${NAMESPACE}_meldegruppeendring_dagpenger_mottatt_total")
            .description("Antall mottatte dagpengermeldegruppeendringer")
            .register(meterRegistry)

    val annenMeldegruppeMottatt: Counter =
        Counter
            .builder("${NAMESPACE}_meldegruppeendring_annet_mottatt_total")
            .description("Antall mottatte annenmeldegruppeendringer")
            .register(meterRegistry)

    val fremtidigMeldegruppeMottatt: Counter =
        Counter
            .builder("${NAMESPACE}_meldegruppeendring_fremtidig_total")
            .description("Antall mottatte meldegruppeendringer med fremtidig dato")
            .register(meterRegistry)
}

class MeldepliktendringMetrikker(
    meterRegistry: MeterRegistry,
) {
    val meldepliktendringMottatt: Counter =
        Counter
            .builder("${NAMESPACE}_meldepliktendring_mottatt_total")
            .description("Antall mottatte meldepliktendringer")
            .register(meterRegistry)

    val fremtidigMeldepliktendringMottatt: Counter =
        Counter
            .builder("${NAMESPACE}_meldepliktendring_fremtidig_total")
            .description("Antall mottatte meldepliktendringer med fremtidig dato")
            .register(meterRegistry)
}

class MeldesyklusErPassertMetrikker(
    meterRegistry: MeterRegistry,
) {
    val meldesyklusErPassertMottatt: Counter =
        Counter
            .builder("${NAMESPACE}_meldesyklus_er_passert_mottatt_total")
            .description("Antall mottatte meldinger om at meldesyklus er passert")
            .register(meterRegistry)
    val meldesyklusErPassertFeilet: Counter =
        Counter
            .builder("${NAMESPACE}_meldesyklus_er_passert_feilet_total")
            .description("Antall behandlinger som har feilet av mottatte meldinger om at meldesyklus er passert")
            .register(meterRegistry)
}

class ArbeidssøkerperiodeMetrikker(
    meterRegistry: MeterRegistry,
) {
    val arbeidssøkerperiodeMottatt: Counter =
        Counter
            .builder("${NAMESPACE}_arbeidssokerperiode_mottatt_total")
            .description("Antall mottatte meldinger om arbeidssøkerperiode")
            .register(meterRegistry)
    val arbeidssøkerperiodeFeilet: Counter =
        Counter
            .builder("${NAMESPACE}_arbeidssokerperiode_feilet_total")
            .description("Antall behandlinger som har feilet av mottatte meldinger om arbeidssøkerperiode")
            .register(meterRegistry)
}

class SynkroniserPersonMetrikker(
    meterRegistry: MeterRegistry,
) {
    val personSynkronisert: Counter =
        Counter
            .builder("${NAMESPACE}_person_synkronisert_total")
            .description("Antall synkroniserte personer via POST endepunkt")
            .register(meterRegistry)
}

class VedtakMetrikker(
    meterRegistry: MeterRegistry,
) {
    val vedtakFattetUtenforArenaMottatt: Counter =
        Counter
            .builder("${NAMESPACE}_vedtak_fattet_utenfor_arena_mottatt_total")
            .description("Antall mottatte meldinger om vedtak fattet utenfor Arena")
            .register(meterRegistry)
    val vedtakFattetUtenforArenaFeilet: Counter =
        Counter
            .builder("${NAMESPACE}_vedtak_fattet_utenfor_arena_feilet_total")
            .description("Antall behandlinger som har feilet av mottatte meldinger om vedtak fattet utenfor Arena")
            .register(meterRegistry)
}

class DatabaseMetrikker(
    meterRegistry: MeterRegistry,
) {
    private val lagredePersoner: AtomicInteger = AtomicInteger(0)
    private val lagredeHendelser: AtomicInteger = AtomicInteger(0)
    private val lagredeFremtidigeHendelser: AtomicInteger = AtomicInteger(0)
    private val antallDagpengebrukere: AtomicInteger = AtomicInteger(0)
    private val antallOvertagelser: AtomicInteger = AtomicInteger(0)

    init {
        Gauge
            .builder("${NAMESPACE}_lagrede_personer_total", lagredePersoner) { it.get().toDouble() }
            .description("Antall lagrede personer i databasen")
            .register(meterRegistry)
        Gauge
            .builder("${NAMESPACE}_lagrede_hendelser_total", lagredeHendelser) { it.get().toDouble() }
            .description("Antall lagrede hendelser i databasen")
            .register(meterRegistry)
        Gauge
            .builder("${NAMESPACE}_lagrede_fremtidige_hendelser_total", lagredeFremtidigeHendelser) {
                it.get().toDouble()
            }.description("Antall lagrede fremtidige hendelser i databasen")
            .register(meterRegistry)
        Gauge
            .builder("${NAMESPACE}_antall_dagpengebrukere_total", antallDagpengebrukere) { it.get().toDouble() }
            .description("Antall dagpengebrukere i databasen")
            .register(meterRegistry)
        Gauge
            .builder("${NAMESPACE}_antall_overtagelser_total", antallOvertagelser) { it.get().toDouble() }
            .description("Antall arbeidssøkere vi har overtatt bekreftelsen for i databasen")
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
                    lagredePersoner.set(personRepository.hentAntallPersoner())
                    lagredeHendelser.set(personRepository.hentAntallHendelser())
                    lagredeFremtidigeHendelser.set(personRepository.hentAntallFremtidigeHendelser())
                    antallDagpengebrukere.set(personRepository.hentAntallDagpengebrukere())
                    antallOvertagelser.set(personRepository.hentAntallOvetagelser())

                    logger.info {
                        "Oppdaterte metrikker med lagrede personer $lagredePersoner, lagrede hendelser $lagredeHendelser, " +
                            "lagrede fremtidige hendelser $lagredeFremtidigeHendelser og antall dagpengebrukere $antallDagpengebrukere"
                    }
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
            .description(ACTION_TIMER_DESCRIPTION)
            .register(meterRegistry)
            .record(tidBrukt.inWholeMilliseconds, MILLISECONDS)

        return blockResult
    }

    suspend fun <T> coTimedAction(
        navn: String,
        block: suspend () -> T,
    ): T {
        val blockResult: T
        val tidBrukt =
            measureTime {
                blockResult = block()
            }
        Timer
            .builder("${NAMESPACE}_timer")
            .tag("navn", navn)
            .description(ACTION_TIMER_DESCRIPTION)
            .register(meterRegistry)
            .record(tidBrukt.inWholeMilliseconds, MILLISECONDS)

        return blockResult
    }

    fun httpTimer(
        navn: String,
        statusCode: HttpStatusCode,
        method: HttpMethod,
        durationSeconds: Number,
    ) = Timer
        .builder("${NAMESPACE}_http_timer")
        .tag("navn", navn)
        .tag("status", statusCode.value.toString())
        .tag("method", method.value)
        .description(ACTION_TIMER_DESCRIPTION)
        .register(meterRegistry)
        .record(durationSeconds.toLong(), SECONDS)
}
