package no.nav.dagpenger.rapportering.personregister.mediator

import KafkaConsumerFactory
import KafkaConsumerRunner
import com.github.navikt.tbd_libs.kafka.AivenConfig
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.ktor.server.engine.embeddedServer
import io.micrometer.core.instrument.Clock
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import io.prometheus.metrics.model.registry.PrometheusRegistry
import no.nav.dagpenger.pdl.createPersonOppslag
import no.nav.dagpenger.rapportering.personregister.mediator.api.internalApi
import no.nav.dagpenger.rapportering.personregister.mediator.api.konfigurasjon
import no.nav.dagpenger.rapportering.personregister.mediator.api.personstatusApi
import no.nav.dagpenger.rapportering.personregister.mediator.connector.ArbeidssøkerConnector
import no.nav.dagpenger.rapportering.personregister.mediator.connector.PdlConnector
import no.nav.dagpenger.rapportering.personregister.mediator.db.PostgresDataSourceBuilder.dataSource
import no.nav.dagpenger.rapportering.personregister.mediator.db.PostgresDataSourceBuilder.runMigration
import no.nav.dagpenger.rapportering.personregister.mediator.db.PostgresPersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.ActionTimer
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.DatabaseMetrikker
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.SoknadMetrikker
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.VedtakMetrikker
import no.nav.dagpenger.rapportering.personregister.mediator.service.ArbeidssøkerService
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.ArbeidssøkerperiodeMottak
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.SøknadMottak
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.VedtakMottak
import no.nav.helse.rapids_rivers.RapidApplication
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
    private val arbeidssøkerService = ArbeidssøkerService(arbeidssøkerConnector)
    private val personstatusMediator = PersonstatusMediator(personRepository, arbeidssøkerService)
    private val pdlConnector = PdlConnector(createPersonOppslag(Configuration.pdlUrl))

    private val rapidsConnection =
        RapidApplication
            .create(
                env = configuration,
                builder = { this.withKtor(embeddedServer(CIOEngine, port = 8080, module = {})) },
            ) { engine, rapid ->
                with(engine.application) {
                    konfigurasjon(meterRegistry)
                    internalApi(meterRegistry)
                    personstatusApi(personRepository, pdlConnector)
                }
                SøknadMottak(rapid, personstatusMediator, soknadMetrikker)
                VedtakMottak(rapid, personstatusMediator, vedtakMetrikker)
            }

    private val arbeidssøkerperiodeConsumer =
        KafkaConsumerRunner(
            kafkaConsumerFactory = KafkaConsumerFactory(AivenConfig.default),
            listener = ArbeidssøkerperiodeMottak(personstatusMediator),
        )

    init {
        rapidsConnection.register(this)
    }

    internal fun start() {
        rapidsConnection.start()
        arbeidssøkerperiodeConsumer.start()
    }

    override fun onStartup(rapidsConnection: RapidsConnection) {
        runMigration()
        databaseMetrikker.startRapporteringJobb(personRepository)
    }
}
