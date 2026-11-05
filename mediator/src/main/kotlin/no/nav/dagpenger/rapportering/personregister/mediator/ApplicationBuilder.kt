package no.nav.dagpenger.rapportering.personregister.mediator

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.navikt.tbd_libs.naisful.naisApp
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerializer
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.ApplicationStopped
import io.micrometer.core.instrument.Clock
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import io.prometheus.metrics.model.registry.PrometheusRegistry
import no.nav.dagpenger.pdl.createPersonOppslag
import no.nav.dagpenger.rapportering.personregister.kafka.KafkaFactory
import no.nav.dagpenger.rapportering.personregister.kafka.KafkaKonfigurasjon
import no.nav.dagpenger.rapportering.personregister.kafka.PaaVegneAvAvroSerializer
import no.nav.dagpenger.rapportering.personregister.mediator.Configuration.config
import no.nav.dagpenger.rapportering.personregister.mediator.Configuration.kafkaSchemaRegistryConfig
import no.nav.dagpenger.rapportering.personregister.mediator.Configuration.kafkaServerKonfigurasjon
import no.nav.dagpenger.rapportering.personregister.mediator.api.personApi
import no.nav.dagpenger.rapportering.personregister.mediator.api.personstatusApi
import no.nav.dagpenger.rapportering.personregister.mediator.connector.ArbeidssøkerBekreftelseKafka
import no.nav.dagpenger.rapportering.personregister.mediator.connector.ArbeidssøkerConnector
import no.nav.dagpenger.rapportering.personregister.mediator.connector.MeldekortregisterConnector
import no.nav.dagpenger.rapportering.personregister.mediator.connector.MeldepliktConnector
import no.nav.dagpenger.rapportering.personregister.mediator.connector.PdlConnector
import no.nav.dagpenger.rapportering.personregister.mediator.db.ArbeidssøkerBeslutningRepositoryPostgres
import no.nav.dagpenger.rapportering.personregister.mediator.db.MeldingerRepositoryPostgres
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepositoryPostgres
import no.nav.dagpenger.rapportering.personregister.mediator.db.PostgresDataSourceBuilder.dataSource
import no.nav.dagpenger.rapportering.personregister.mediator.db.PostgresDataSourceBuilder.runMigration
import no.nav.dagpenger.rapportering.personregister.mediator.db.TempPersonRepositoryPostgres
import no.nav.dagpenger.rapportering.personregister.mediator.jobs.AktiverHendelserJob
import no.nav.dagpenger.rapportering.personregister.mediator.jobs.ScheduledTask
import no.nav.dagpenger.rapportering.personregister.mediator.jobs.TaskExecutor
import no.nav.dagpenger.rapportering.personregister.mediator.jobs.midlertidig.MeldestatusJob
import no.nav.dagpenger.rapportering.personregister.mediator.jobs.midlertidig.ResendPåVegneAvMelding
import no.nav.dagpenger.rapportering.personregister.mediator.kafka.KafkaConfig
import no.nav.dagpenger.rapportering.personregister.mediator.kafka.KafkaLytter
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.ActionTimer
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.ArbeidssøkerBekreftelseFraDpMeldekortregisterMetrikker
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.ArbeidssøkerBekreftelseTilArbeidssøkerregisteretMetrikker
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.ArbeidssøkerperiodeMetrikker
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.AvsluttetArbeidssøkerperiodeMetrikker
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.BehandlingsresultatMetrikker
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.DatabaseMetrikker
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.IkkeMeldtSegPå21DagerMetrikker
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.JobbkjøringMetrikker
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.MeldegruppeendringMetrikker
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.MeldepliktendringMetrikker
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.MeldestatusMetrikker
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.SynkroniserPersonMetrikker
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.SøknadMetrikker
import no.nav.dagpenger.rapportering.personregister.mediator.observers.ArbeidssøkerBeslutningObserver
import no.nav.dagpenger.rapportering.personregister.mediator.observers.PersonObserverKafka
import no.nav.dagpenger.rapportering.personregister.mediator.observers.PersonObserverMeldekortregister
import no.nav.dagpenger.rapportering.personregister.mediator.service.ArbeidssøkerBekreftelseService
import no.nav.dagpenger.rapportering.personregister.mediator.service.ArbeidssøkerService
import no.nav.dagpenger.rapportering.personregister.mediator.service.ManuellMigreringAvMeldekortForBrukereSomBareHarAvslagService
import no.nav.dagpenger.rapportering.personregister.mediator.service.PersonService
import no.nav.dagpenger.rapportering.personregister.mediator.service.SøknadService
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.ArbeidssøkerBekreftelseMottak
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.ArbeidssøkerMottak
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.ArbeidssøkerperiodeOvertakelseMottak
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.BehandlingsresultatMottak
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.IkkeMeldtSegPå21DagerMottak
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.ManuellMigreringAvMeldekortForBrukereSomBareHarAvslagMottak
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.MeldekortTestdataMottak
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.MeldestatusMottak
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.NødbremsMottak
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.StartAktiverHendelserJobManueltMottak
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.SøknadMottak
import no.nav.dagpenger.rapportering.personregister.modell.Ident
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.paw.arbeidssokerregisteret.api.v1.Periode
import no.nav.paw.bekreftelse.melding.v1.Bekreftelse
import no.nav.paw.bekreftelse.paavegneav.v1.PaaVegneAv
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.common.serialization.LongSerializer
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

internal class ApplicationBuilder(
    configuration: Map<String, String>,
) : RapidsConnection.StatusListener {
    companion object {
        private lateinit var rapidsConnection: RapidsConnection

        fun getRapidsConnection(): RapidsConnection = rapidsConnection
    }

    private val meterRegistry =
        PrometheusMeterRegistry(PrometheusConfig.DEFAULT, PrometheusRegistry.defaultRegistry, Clock.SYSTEM)
    private val søknadMetrikker = SøknadMetrikker(meterRegistry)
    private val behandlingsresultatMetrikker = BehandlingsresultatMetrikker(meterRegistry)
    private val meldestatusMetrikker = MeldestatusMetrikker(meterRegistry)
    private val meldegruppeendringMetrikker = MeldegruppeendringMetrikker(meterRegistry)
    private val ikkeMeldtSegPå21DagerMetrikker = IkkeMeldtSegPå21DagerMetrikker(meterRegistry)
    private val meldepliktendringMetrikker = MeldepliktendringMetrikker(meterRegistry)
    private val arbeidssøkerperiodeMetrikker = ArbeidssøkerperiodeMetrikker(meterRegistry)
    private val arbeidssøkerBekreftelseFraDpMeldekortregisterMetrikker =
        ArbeidssøkerBekreftelseFraDpMeldekortregisterMetrikker(meterRegistry)
    private val arbeidssøkerBekreftelseTilArbeidssøkerregisteretMetrikker =
        ArbeidssøkerBekreftelseTilArbeidssøkerregisteretMetrikker(meterRegistry)
    private val avsluttetArbeidssøkerperiodeMetrikker = AvsluttetArbeidssøkerperiodeMetrikker(meterRegistry)
    private val synkroniserPersonMetrikker = SynkroniserPersonMetrikker(meterRegistry)
    private val databaseMetrikker = DatabaseMetrikker(meterRegistry)
    private val actionTimer = ActionTimer(meterRegistry)

    // TODO: Bytte ut lokal cache med Valkey
    private val pdlIdentCache: Cache<String, List<Ident>> =
        Caffeine
            .newBuilder()
            .expireAfterWrite(15, TimeUnit.MINUTES)
            .maximumSize(10000)
            .build()

    private val personRepository = PersonRepositoryPostgres(dataSource, actionTimer)
    private val arbeidssøkerBeslutningRepository = ArbeidssøkerBeslutningRepositoryPostgres(dataSource, actionTimer)
    private val meldingerRepository = MeldingerRepositoryPostgres(dataSource)

    private val tempPersonRepository = TempPersonRepositoryPostgres(dataSource)

    private val arbeidssøkerConnector = ArbeidssøkerConnector(actionTimer = actionTimer)
    private val meldepliktConnector = MeldepliktConnector(actionTimer = actionTimer)
    private val meldekortregisterConnector = MeldekortregisterConnector(actionTimer = actionTimer)

    private val bekreftelsePåVegneAvTopic = configuration.getValue("BEKREFTELSE_PAA_VEGNE_AV_TOPIC")
    private val arbeidssøkerperioderTopic = configuration.getValue("ARBEIDSSOKERPERIODER_TOPIC")
    private val kafkaKonfigurasjon = KafkaKonfigurasjon(kafkaServerKonfigurasjon, kafkaSchemaRegistryConfig)
    private val kafkaFactory = KafkaFactory(kafkaKonfigurasjon)
    private val bekreftelsePåVegneAvProdusent =
        kafkaFactory.createProducer<Long, PaaVegneAv>(
            clientId = "teamdagpenger-personregister-paavegneav-producer",
            keySerializer = LongSerializer::class,
            valueSerializer = PaaVegneAvAvroSerializer::class,
        )

    private val personObserverKafka =
        PersonObserverKafka(
            bekreftelsePåVegneAvProdusent,
            arbeidssøkerConnector,
            bekreftelsePåVegneAvTopic,
            meldingerRepository,
        )
    private val arbeidssøkerBeslutningObserver =
        ArbeidssøkerBeslutningObserver(
            arbeidssøkerBeslutningRepository,
        )
    private val personObserverMeldekortregister = PersonObserverMeldekortregister(personRepository, meldingerRepository)

    private val pdlConnector = PdlConnector(createPersonOppslag(Configuration.pdlUrl), actionTimer, pdlIdentCache)
    private val personService =
        PersonService(
            pdlConnector,
            personRepository,
            listOf(personObserverKafka, arbeidssøkerBeslutningObserver, personObserverMeldekortregister),
            meldekortregisterConnector,
        )
    private val arbeidssøkerService =
        ArbeidssøkerService(
            personRepository,
            arbeidssøkerConnector,
            meldekortregisterConnector,
            { getRapidsConnection() },
            avsluttetArbeidssøkerperiodeMetrikker,
            meldingerRepository,
        )
    private val arbeidssøkerMediator =
        ArbeidssøkerMediator(
            arbeidssøkerService,
            personRepository,
            personService,
            listOf(personObserverKafka, arbeidssøkerBeslutningObserver),
            actionTimer,
        )
    private val meldepliktMediator =
        MeldepliktMediator(
            personRepository,
            personService,
            listOf(personObserverKafka, arbeidssøkerBeslutningObserver),
            meldepliktConnector,
            actionTimer,
        )
    private val personMediator =
        PersonMediator(
            personRepository,
            personService,
            arbeidssøkerMediator,
            listOf(personObserverKafka, arbeidssøkerBeslutningObserver),
            meldepliktMediator,
            actionTimer,
        )
    private val søknadService =
        SøknadService(
            personService,
            arbeidssøkerMediator,
            actionTimer,
        )
    private val fremtidigHendelseMediator = FremtidigHendelseMediator(personRepository, actionTimer)
    private val meldestatusMediator =
        MeldestatusMediator(
            personRepository,
            personService,
            meldepliktConnector,
            meldepliktMediator,
            personMediator,
            fremtidigHendelseMediator,
            meldepliktendringMetrikker,
            meldegruppeendringMetrikker,
            actionTimer,
        )

    private val arbeidssøkerMottak =
        ArbeidssøkerMottak(
            arbeidssøkerMediator,
            arbeidssøkerperiodeMetrikker,
            arbeidssøkerService,
            meldingerRepository,
        )
    private val overtakelseMottak = ArbeidssøkerperiodeOvertakelseMottak(arbeidssøkerMediator, meldingerRepository)
    private val bekreftelseKafkaProdusent =
        kafkaFactory.createProducer<Long, Bekreftelse>(
            clientId = "teamdagpenger-rapportering-producer",
            keySerializer = LongSerializer::class,
            valueSerializer = SpecificAvroSerializer<Bekreftelse>()::class,
        )
    private val arbeidssøkerBekreftelseKafka =
        ArbeidssøkerBekreftelseKafka(
            bekreftelseKafkaProdusent,
            arbeidssøkerBekreftelseTilArbeidssøkerregisteretMetrikker,
            meldingerRepository,
        )

    private val aktiverHendelserJob =
        AktiverHendelserJob(
            personRepository,
            personService,
            personMediator,
            meldestatusMediator,
            meldepliktConnector,
            jobbkjøringMetrikker = JobbkjøringMetrikker(meterRegistry, AktiverHendelserJob::class.simpleName!!),
        )
    private val taskExecutor =
        TaskExecutor(
            listOf(
                ScheduledTask(aktiverHendelserJob, 0, 1),
            ),
        )

    // Midlertidige
    private val resendPaaVegneAvJob = ResendPåVegneAvMelding()
    private val meldestatusJob = MeldestatusJob()

    private val kafkaContext =
        KafkaContext(
            bekreftelsePåVegneAvKafkaProdusent = bekreftelsePåVegneAvProdusent,
        )

    init {
        rapidsConnection =
            RapidApplication
                .create(
                    env = configuration,
                    builder = {
                        withKtor { preStopHook, rapid ->
                            naisApp(
                                meterRegistry = meterRegistry,
                                objectMapper = Configuration.defaultObjectMapper,
                                applicationLogger = LoggerFactory.getLogger("ApplicationLogger"),
                                callLogger = LoggerFactory.getLogger("CallLogger"),
                                aliveCheck = rapid::isRunning,
                                readyCheck = rapid::isReady,
                                preStopHook = preStopHook::handlePreStopRequest,
                                statusPagesConfig = { statusPagesConfig() },
                            ) {
                                monitor.subscribe(ApplicationStopped) {
                                    logger.info { "Forsøker å lukke datasource..." }
                                    dataSource.close()
                                    logger.info { "Lukket datasource" }
                                }

                                pluginConfiguration(kafkaContext)
                                personstatusApi(personMediator, synkroniserPersonMetrikker, personService)
                                personApi(personService, søknadService, arbeidssøkerService)
                            }
                        }
                    },
                ) { _, rapid ->
                    logger.info { "Starter rapid with" }
                    logger.info { "config: $config" }

                    MeldekortTestdataMottak(rapid)
                    MeldestatusMottak(
                        rapid,
                        meldestatusMediator,
                        meldestatusMetrikker,
                        meldingerRepository,
                    )
                    IkkeMeldtSegPå21DagerMottak(
                        rapid,
                        personMediator,
                        ikkeMeldtSegPå21DagerMetrikker,
                        meldingerRepository,
                    )
                    SøknadMottak(rapid, søknadService, søknadMetrikker, meldingerRepository)
                    BehandlingsresultatMottak(
                        rapid,
                        personRepository,
                        personMediator,
                        fremtidigHendelseMediator,
                        behandlingsresultatMetrikker,
                        meldingerRepository,
                    )
                    NødbremsMottak(rapid, personMediator, meldingerRepository)
                    ArbeidssøkerBekreftelseMottak(
                        rapid,
                        ArbeidssøkerBekreftelseService(
                            arbeidssøkerConnector,
                            arbeidssøkerBekreftelseKafka,
                            personRepository,
                        ),
                        arbeidssøkerBekreftelseFraDpMeldekortregisterMetrikker,
                        meldingerRepository,
                    )
                    StartAktiverHendelserJobManueltMottak(
                        rapidsConnection = rapid,
                        aktiverHendelserJob = aktiverHendelserJob,
                        meldingerRepository = meldingerRepository,
                    )
                    ManuellMigreringAvMeldekortForBrukereSomBareHarAvslagMottak(
                        rapidsConnection = rapid,
                        ManuellMigreringAvMeldekortForBrukereSomBareHarAvslagService(
                            personRepository,
                            personObserverMeldekortregister,
                        ),
                    )
                }

        rapidsConnection.register(this)

        val bekreftelsePåVegneAvKafkaLytter =
            KafkaLytter<PaaVegneAv>(
                KafkaConfig.consumer<PaaVegneAv>(
                    topicName = bekreftelsePåVegneAvTopic,
                    groupId = "teamdagpenger-personregister-paavegneav-v1",
                ),
                rapidsConnection,
            ) { korrelasjonsId, paaVegneAv ->
                overtakelseMottak.consume(korrelasjonsId, paaVegneAv)
            }
        val arbeidssøkerperioderKafkaLytter =
            KafkaLytter<Periode>(
                KafkaConfig.consumer<Periode>(
                    topicName = arbeidssøkerperioderTopic,
                    groupId = "teamdagpenger-personregister-arbeidssokerperiode-v1",
                ),
                rapidsConnection,
            ) { korrelasjonsId, periode ->
                arbeidssøkerMottak.consume(korrelasjonsId, periode)
            }
        rapidsConnection.register(bekreftelsePåVegneAvKafkaLytter)
        rapidsConnection.register(arbeidssøkerperioderKafkaLytter)
    }

    internal fun start() {
        rapidsConnection.start()
    }

    override fun onStartup(rapidsConnection: RapidsConnection) {
        runMigration()
        databaseMetrikker.startRapporteringJobb(personRepository)
        taskExecutor.startExecution()

        // Midlertidige
        resendPaaVegneAvJob.start(personRepository, personService)
        meldestatusJob.start(personService, tempPersonRepository, meldepliktConnector, meldestatusMediator)
    }
}

data class KafkaContext(
    val bekreftelsePåVegneAvKafkaProdusent: Producer<Long, PaaVegneAv>,
)
