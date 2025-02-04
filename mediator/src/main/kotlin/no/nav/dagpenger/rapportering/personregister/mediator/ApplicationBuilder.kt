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
import no.nav.dagpenger.rapportering.personregister.mediator.Configuration.kafkaSchemaRegistryConfig
import no.nav.dagpenger.rapportering.personregister.mediator.Configuration.kafkaServerKonfigurasjon
import no.nav.dagpenger.rapportering.personregister.mediator.api.internalApi
import no.nav.dagpenger.rapportering.personregister.mediator.api.konfigurasjon
import no.nav.dagpenger.rapportering.personregister.mediator.api.personstatusApi
import no.nav.dagpenger.rapportering.personregister.mediator.db.PostgresDataSourceBuilder.dataSource
import no.nav.dagpenger.rapportering.personregister.mediator.db.PostgresDataSourceBuilder.runMigration
import no.nav.dagpenger.rapportering.personregister.mediator.db.PostgresPersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.db.PostrgesArbeidssøkerRepository
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.ActionTimer
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.DatabaseMetrikker
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.SoknadMetrikker
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.VedtakMetrikker
import no.nav.dagpenger.rapportering.personregister.mediator.service.ArbeidssøkerService
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.ArbeidssøkerperiodeMottak
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.ArbeidssøkerregisterLøsningMottak
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.MeldegruppeendringMottak
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.SøknadMottak
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.VedtakMottak
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.paw.bekreftelse.paavegneav.v1.PaaVegneAv
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
    private val arbeidssøkerRepository = PostrgesArbeidssøkerRepository(dataSource, actionTimer)

    private val overtaBekreftelseTopic = configuration.getValue("OVERTA_BEKREFTELSE_TOPIC")
    private val kafkaKonfigurasjon = KafkaKonfigurasjon(kafkaServerKonfigurasjon, kafkaSchemaRegistryConfig)
    private val kafkaFactory = KafkaFactory(kafkaKonfigurasjon)
    private val overtaBekreftelseKafkaProdusent =
        kafkaFactory.createProducer<Long, PaaVegneAv>(
            clientId = "teamdagpenger-arbeidssokerregister-producer",
            keySerializer = LongSerializer::class,
            valueSerializer = PaaVegneAvAvroSerializer::class,
        )

    private val rapidsConnection =
        RapidApplication
            .create(
                env = configuration,
                builder = { this.withKtor(embeddedServer(CIOEngine, port = 8080, module = {})) },
            ) { engine, rapid ->
                val arbeidssøkerService = ArbeidssøkerService(rapid, personRepository, arbeidssøkerRepository)

                with(engine.application) {
                    konfigurasjon(meterRegistry)
                    internalApi(meterRegistry)
                    personstatusApi(personRepository, arbeidssøkerService)
                }
                val personstatusMediator = PersonstatusMediator(personRepository, arbeidssøkerService)
                val arbeidssøkerMediator = ArbeidssøkerMediator(arbeidssøkerService)
                SøknadMottak(rapid, personstatusMediator, soknadMetrikker)
                VedtakMottak(rapid, personstatusMediator, vedtakMetrikker)
                MeldegruppeendringMottak(rapid, personstatusMediator)
                ArbeidssøkerperiodeMottak(rapid, personstatusMediator)
                ArbeidssøkerregisterLøsningMottak(rapid, arbeidssøkerMediator)
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
    }
}
