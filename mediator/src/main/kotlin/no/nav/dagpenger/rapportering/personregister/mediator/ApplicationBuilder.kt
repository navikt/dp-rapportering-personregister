package no.nav.dagpenger.rapportering.personregister.mediator

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.ktor.server.engine.embeddedServer
import io.micrometer.core.instrument.Clock
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import io.prometheus.metrics.model.registry.PrometheusRegistry
import no.nav.dagpenger.rapportering.personregister.kafka.KafkaFactory
import no.nav.dagpenger.rapportering.personregister.kafka.KafkaKonfigurasjon
import no.nav.dagpenger.rapportering.personregister.kafka.PaaVegneAvAvroSerializer
import no.nav.dagpenger.rapportering.personregister.kafka.PeriodeAvroDeserializer
import no.nav.dagpenger.rapportering.personregister.mediator.Configuration.kafkaSchemaRegistryConfig
import no.nav.dagpenger.rapportering.personregister.mediator.Configuration.kafkaServerKonfigurasjon
import no.nav.dagpenger.rapportering.personregister.mediator.api.internalApi
import no.nav.dagpenger.rapportering.personregister.mediator.api.personstatusApi
import no.nav.dagpenger.rapportering.personregister.mediator.connector.ArbeidssøkerConnector
import no.nav.dagpenger.rapportering.personregister.mediator.db.PostgresDataSourceBuilder.dataSource
import no.nav.dagpenger.rapportering.personregister.mediator.db.PostgresDataSourceBuilder.runMigration
import no.nav.dagpenger.rapportering.personregister.mediator.db.PostgresPersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.jobs.AktiverHendelserJob
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.ActionTimer
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.DatabaseMetrikker
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.SoknadMetrikker
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.VedtakMetrikker
import no.nav.dagpenger.rapportering.personregister.mediator.observers.PersonObserverKafka
import no.nav.dagpenger.rapportering.personregister.mediator.service.ArbeidssøkerService
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.ArbeidssøkerMottak
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.MeldegruppeendringMottak
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.MeldepliktendringMottak
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.SøknadMottak
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.paw.arbeidssokerregisteret.api.v1.Periode
import no.nav.paw.bekreftelse.paavegneav.v1.PaaVegneAv
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.common.serialization.LongDeserializer
import org.apache.kafka.common.serialization.LongSerializer
import io.ktor.server.cio.CIO as CIOEngine

internal class ApplicationBuilder(
    configuration: Map<String, String>,
) : RapidsConnection.StatusListener {
    private val meterRegistry =
        PrometheusMeterRegistry(PrometheusConfig.DEFAULT, PrometheusRegistry.defaultRegistry, Clock.SYSTEM)
    private val soknadMetrikker = SoknadMetrikker(meterRegistry)
    private val vedtakMetrikker = VedtakMetrikker(meterRegistry)
    private val databaseMetrikker = DatabaseMetrikker(meterRegistry)
    private val actionTimer = ActionTimer(meterRegistry)

    private val personRepository = PostgresPersonRepository(dataSource, actionTimer)
    private val arbeidssøkerConnector = ArbeidssøkerConnector()

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

    private val arbeidssøkerService = ArbeidssøkerService(arbeidssøkerConnector)
    private val arbeidssøkerMediator = ArbeidssøkerMediator(arbeidssøkerService, personRepository)
    private val arbeidssøkerMottak = ArbeidssøkerMottak(arbeidssøkerMediator)
    private val kafkaContext =
        KafkaContext(
            bekreftelsePåVegneAvProdusent,
            arbeidssøkerperioderKafkaConsumer,
            arbeidssøkerperioderTopic,
            arbeidssøkerMottak,
        )

    private val personstatusMediator = PersonstatusMediator(personRepository, arbeidssøkerMediator, listOf(personObserverKafka))
    private val fremtidigHendelseMediator = FremtidigHendelseMediator(personRepository)
    private val aktiverHendelserJob = AktiverHendelserJob()
    private val rapidsConnection =
        RapidApplication
            .create(
                env = configuration,
                builder = {
                    this.withKtor(
                        embeddedServer(CIOEngine, port = 8080, module = {
                            /*install(KtorServerTelemetry) {
                                setOpenTelemetry(openTelemetry)
                            }*/
                        }),
                    )
                },
            ) { engine, rapid ->
                val arbeidssøkerMediator = ArbeidssøkerMediator(arbeidssøkerService, personRepository, listOf(personObserverKafka))
                with(engine.application) {
                    pluginConfiguration(meterRegistry, kafkaContext)
                    internalApi(meterRegistry)
                    personstatusApi(personRepository, arbeidssøkerMediator, personstatusMediator)
                }

                SøknadMottak(rapid, personstatusMediator, soknadMetrikker)
                MeldegruppeendringMottak(rapid, personstatusMediator, fremtidigHendelseMediator)
                MeldepliktendringMottak(rapid, personstatusMediator, fremtidigHendelseMediator)
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
        aktiverHendelserJob.start(personRepository, personstatusMediator)
    }
}

data class KafkaContext(
    val bekreftelsePåVegneAvKafkaProdusent: Producer<Long, PaaVegneAv>,
    val arbeidssøkerperioderKafkaConsumer: KafkaConsumer<Long, Periode>,
    val arbeidssøkerperioderTopic: String,
    val arbeidssøkerMottak: ArbeidssøkerMottak,
)
