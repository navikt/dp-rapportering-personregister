package no.nav.dagpenger.rapportering.personregister.mediator

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.ktor.server.engine.embeddedServer
import io.micrometer.core.instrument.Clock
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import io.prometheus.metrics.model.registry.PrometheusRegistry
import mu.KotlinLogging
import no.nav.dagpenger.pdl.createPersonOppslag
import no.nav.dagpenger.rapportering.personregister.kafka.KafkaFactory
import no.nav.dagpenger.rapportering.personregister.kafka.KafkaKonfigurasjon
import no.nav.dagpenger.rapportering.personregister.kafka.PaaVegneAvAvroDeserializer
import no.nav.dagpenger.rapportering.personregister.kafka.PaaVegneAvAvroSerializer
import no.nav.dagpenger.rapportering.personregister.kafka.PeriodeAvroDeserializer
import no.nav.dagpenger.rapportering.personregister.mediator.Configuration.config
import no.nav.dagpenger.rapportering.personregister.mediator.Configuration.kafkaSchemaRegistryConfig
import no.nav.dagpenger.rapportering.personregister.mediator.Configuration.kafkaServerKonfigurasjon
import no.nav.dagpenger.rapportering.personregister.mediator.api.internalApi
import no.nav.dagpenger.rapportering.personregister.mediator.api.personstatusApi
import no.nav.dagpenger.rapportering.personregister.mediator.connector.ArbeidssøkerConnector
import no.nav.dagpenger.rapportering.personregister.mediator.connector.MeldepliktConnector
import no.nav.dagpenger.rapportering.personregister.mediator.connector.PdlConnector
import no.nav.dagpenger.rapportering.personregister.mediator.db.PostgresDataSourceBuilder.dataSource
import no.nav.dagpenger.rapportering.personregister.mediator.db.PostgresDataSourceBuilder.runMigration
import no.nav.dagpenger.rapportering.personregister.mediator.db.PostgresPersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.db.PostgresTempPersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.db.PostgressArbeidssøkerBeslutningRepository
import no.nav.dagpenger.rapportering.personregister.mediator.jobs.AktiverHendelserJob
import no.nav.dagpenger.rapportering.personregister.mediator.jobs.AvsluttetPerioderJob
import no.nav.dagpenger.rapportering.personregister.mediator.jobs.AvvikStatusJob
import no.nav.dagpenger.rapportering.personregister.mediator.jobs.ResendPåVegneAvMelding
import no.nav.dagpenger.rapportering.personregister.mediator.jobs.RettPersonStatusJob
import no.nav.dagpenger.rapportering.personregister.mediator.jobs.SendPaaVegneAvForAlleJob
import no.nav.dagpenger.rapportering.personregister.mediator.jobs.SlettPersonerJob
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.ActionTimer
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.ArbeidssøkerperiodeMetrikker
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.DatabaseMetrikker
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.MeldegruppeendringMetrikker
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.MeldepliktendringMetrikker
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.SoknadMetrikker
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.SynkroniserPersonMetrikker
import no.nav.dagpenger.rapportering.personregister.mediator.observers.ArbeidssøkerBeslutningObserver
import no.nav.dagpenger.rapportering.personregister.mediator.observers.PersonObserverKafka
import no.nav.dagpenger.rapportering.personregister.mediator.service.ArbeidssøkerService
import no.nav.dagpenger.rapportering.personregister.mediator.service.PersonService
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.ArbeidssøkerMottak
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.ArbeidssøkerperiodeOvertakelseMottak
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.MeldegruppeendringMottak
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.MeldepliktendringMottak
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.SøknadMottak
import no.nav.dagpenger.rapportering.personregister.modell.Ident
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.paw.arbeidssokerregisteret.api.v1.Periode
import no.nav.paw.bekreftelse.paavegneav.v1.PaaVegneAv
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.common.serialization.LongDeserializer
import org.apache.kafka.common.serialization.LongSerializer
import java.util.concurrent.TimeUnit
import io.ktor.server.cio.CIO as CIOEngine

private val logger = KotlinLogging.logger {}

internal class ApplicationBuilder(
    configuration: Map<String, String>,
) : RapidsConnection.StatusListener {
    private val meterRegistry =
        PrometheusMeterRegistry(PrometheusConfig.DEFAULT, PrometheusRegistry.defaultRegistry, Clock.SYSTEM)
    private val soknadMetrikker = SoknadMetrikker(meterRegistry)
    private val meldegruppeendringMetrikker = MeldegruppeendringMetrikker(meterRegistry)
    private val meldepliktendringMetrikker = MeldepliktendringMetrikker(meterRegistry)
    private val arbeidssøkerperiodeMetrikker = ArbeidssøkerperiodeMetrikker(meterRegistry)
    private val synkroniserPersonMetrikker = SynkroniserPersonMetrikker(meterRegistry)
    private val databaseMetrikker = DatabaseMetrikker(meterRegistry)
    private val actionTimer = ActionTimer(meterRegistry)

    // TODO: Bytte ut lokal cache med Valkey
    private val pdlIdentCache: Cache<String, List<Ident>> =
        Caffeine
            .newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .maximumSize(1000)
            .build()

    private val personRepository = PostgresPersonRepository(dataSource, actionTimer)
    private val tempPersonRepository = PostgresTempPersonRepository(dataSource)
    private val arbeidssøkerBeslutningRepository = PostgressArbeidssøkerBeslutningRepository(dataSource, actionTimer)
    private val arbeidssøkerConnector = ArbeidssøkerConnector(actionTimer = actionTimer)
    private val meldepliktConnector = MeldepliktConnector(actionTimer = actionTimer)

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
    private val bekreftelsePåVegneAvKafkaConsumer =
        kafkaFactory.createConsumer<Long, PaaVegneAv>(
            groupId = "teamdagpenger-personregister-paavegneav-v1",
            clientId = "teamdagpenger-personregister-paavegneav-consumer",
            keyDeserializer = LongDeserializer::class,
            valueDeserializer = PaaVegneAvAvroDeserializer::class,
            autoCommit = false,
        )
    private val arbeidssøkerperioderKafkaConsumer =
        kafkaFactory.createConsumer<Long, Periode>(
            groupId = "teamdagpenger-personregister-arbeidssokerperiode-v1",
            clientId = "teamdagpenger-personregister-arbeidssokerperiode-consumer",
            keyDeserializer = LongDeserializer::class,
            valueDeserializer = PeriodeAvroDeserializer::class,
        )

    private val personObserverKafka =
        PersonObserverKafka(
            bekreftelsePåVegneAvProdusent,
            arbeidssøkerConnector,
            bekreftelsePåVegneAvTopic,
        )

    private val arbeidssøkerBeslutningObserver =
        ArbeidssøkerBeslutningObserver(
            arbeidssøkerBeslutningRepository,
        )
    private val pdlConnector = PdlConnector(createPersonOppslag(Configuration.pdlUrl))
    private val personService =
        PersonService(
            pdlConnector,
            personRepository,
            listOf(personObserverKafka, arbeidssøkerBeslutningObserver),
            pdlIdentCache,
        )
    private val arbeidssøkerService = ArbeidssøkerService(arbeidssøkerConnector)
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
    private val fremtidigHendelseMediator = FremtidigHendelseMediator(personRepository, actionTimer)
    private val arbeidssøkerMottak = ArbeidssøkerMottak(arbeidssøkerMediator, arbeidssøkerperiodeMetrikker)
    private val overtakelseMottak = ArbeidssøkerperiodeOvertakelseMottak(arbeidssøkerMediator)
    private val aktiverHendelserJob = AktiverHendelserJob()
    private val slettPersonerJob = SlettPersonerJob()
    private val resendPaaVegneAvJob = ResendPåVegneAvMelding()
    private val rettPersonStatusJob = RettPersonStatusJob()
    private val sendPaaVegneAvForAlleJob = SendPaaVegneAvForAlleJob()
    private val avvikStatusJob = AvvikStatusJob()
    private val avsluttetPerioderJob = AvsluttetPerioderJob()
    private val kafkaContext =
        KafkaContext(
            bekreftelsePåVegneAvKafkaProdusent = bekreftelsePåVegneAvProdusent,
            bekreftelsePåVegneAvKafkaConsumer = bekreftelsePåVegneAvKafkaConsumer,
            påVegneAvMottak = overtakelseMottak,
            påVegneAvTopic = bekreftelsePåVegneAvTopic,
            arbeidssøkerperioderKafkaConsumer = arbeidssøkerperioderKafkaConsumer,
            arbeidssøkerperioderTopic = arbeidssøkerperioderTopic,
            arbeidssøkerMottak = arbeidssøkerMottak,
        )

    private val rapidsConnection =
        RapidApplication
            .create(
                env = configuration,
                builder = {
                    this.withKtor(
                        embeddedServer(CIOEngine, port = 8080, module = {}),
                    )
                },
            ) { engine, rapid ->
                logger.info { "Starter rapid with" }
                logger.info { "config: $config" }

                with(engine.application) {
                    pluginConfiguration(meterRegistry, kafkaContext)
                    internalApi(meterRegistry)
                    personstatusApi(personMediator, synkroniserPersonMetrikker, personService)
                }

                SøknadMottak(rapid, personMediator, soknadMetrikker)
                MeldegruppeendringMottak(rapid, personMediator, fremtidigHendelseMediator, meldegruppeendringMetrikker)
                MeldepliktendringMottak(rapid, meldepliktMediator, fremtidigHendelseMediator, meldepliktendringMetrikker)
            }

    init {
        rapidsConnection.register(this)
    }

    internal fun start() {
        rapidsConnection.start()
    }

    override fun onStartup(rapidsConnection: RapidsConnection) {
        runMigration()
        databaseMetrikker.startRapporteringJobb(personRepository)
        aktiverHendelserJob.start(personRepository, personMediator, meldepliktMediator)
        slettPersonerJob.start(personRepository)
        avvikStatusJob.start(personRepository, tempPersonRepository, listOf(personObserverKafka))
//        resendPaaVegneAvJob.start(personRepository, personService)
    }
}

data class KafkaContext(
    val bekreftelsePåVegneAvKafkaProdusent: Producer<Long, PaaVegneAv>,
    val bekreftelsePåVegneAvKafkaConsumer: KafkaConsumer<Long, PaaVegneAv>,
    val påVegneAvMottak: ArbeidssøkerperiodeOvertakelseMottak,
    val påVegneAvTopic: String,
    val arbeidssøkerperioderKafkaConsumer: KafkaConsumer<Long, Periode>,
    val arbeidssøkerperioderTopic: String,
    val arbeidssøkerMottak: ArbeidssøkerMottak,
)
