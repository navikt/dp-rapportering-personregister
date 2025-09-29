package no.nav.dagpenger.rapportering.personregister.mediator.api

import com.github.benmanes.caffeine.cache.Caffeine
import io.getunleash.Unleash
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.micrometer.core.instrument.Clock
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.prometheus.metrics.model.registry.PrometheusRegistry
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.dagpenger.rapportering.personregister.kafka.PaaVegneAvAvroDeserializer
import no.nav.dagpenger.rapportering.personregister.kafka.PeriodeAvroDeserializer
import no.nav.dagpenger.rapportering.personregister.mediator.ArbeidssøkerMediator
import no.nav.dagpenger.rapportering.personregister.mediator.KafkaContext
import no.nav.dagpenger.rapportering.personregister.mediator.MeldepliktMediator
import no.nav.dagpenger.rapportering.personregister.mediator.PersonMediator
import no.nav.dagpenger.rapportering.personregister.mediator.connector.ArbeidssøkerConnector
import no.nav.dagpenger.rapportering.personregister.mediator.connector.MeldepliktConnector
import no.nav.dagpenger.rapportering.personregister.mediator.connector.PdlConnector
import no.nav.dagpenger.rapportering.personregister.mediator.db.BehandlingRepositoryPostgres
import no.nav.dagpenger.rapportering.personregister.mediator.db.Postgres.database
import no.nav.dagpenger.rapportering.personregister.mediator.db.PostgresDataSourceBuilder.dataSource
import no.nav.dagpenger.rapportering.personregister.mediator.db.PostgresDataSourceBuilder.runMigration
import no.nav.dagpenger.rapportering.personregister.mediator.db.PostgresPersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.pluginConfiguration
import no.nav.dagpenger.rapportering.personregister.mediator.service.ArbeidssøkerService
import no.nav.dagpenger.rapportering.personregister.mediator.service.PersonService
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.ArbeidssøkerMottak
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.ArbeidssøkerperiodeOvertakelseMottak
import no.nav.dagpenger.rapportering.personregister.mediator.utils.MetrikkerTestUtil.actionTimer
import no.nav.dagpenger.rapportering.personregister.mediator.utils.MetrikkerTestUtil.arbeidssøkerperiodeMetrikker
import no.nav.dagpenger.rapportering.personregister.mediator.utils.MetrikkerTestUtil.synkroniserPersonMetrikker
import no.nav.dagpenger.rapportering.personregister.mediator.utils.kafka.TestKafkaContainer
import no.nav.dagpenger.rapportering.personregister.mediator.utils.kafka.TestKafkaProducer
import no.nav.dagpenger.rapportering.personregister.modell.PersonObserver
import no.nav.paw.bekreftelse.paavegneav.v1.PaaVegneAv
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.mock.oauth2.token.DefaultOAuth2TokenCallback
import org.apache.kafka.common.serialization.LongDeserializer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import java.util.concurrent.TimeUnit

open class ApiTestSetup {
    val arbeidssøkerConnector = mockk<ArbeidssøkerConnector>(relaxed = true)
    val meldepliktConnector = mockk<MeldepliktConnector>(relaxed = true)
    val personObserver = mockk<PersonObserver>(relaxed = true)
    val pdlConnector = mockk<PdlConnector>()
    val unleash = mockk<Unleash>()
    lateinit var behandlingRepository: BehandlingRepositoryPostgres

    companion object {
        const val TOKENX_ISSUER_ID = "tokenx"
        const val REQUIRED_AUDIENCE = "tokenx"
        const val AZURE_APP_CLIENT_ID = "test_client_id"
        const val AZURE_OPENID_CONFIG_ISSUER = "test_issuer"

        var mockOAuth2Server = MockOAuth2Server()

        @BeforeAll
        @JvmStatic
        fun setup() {
            try {
                println("Start mockserver")
                mockOAuth2Server = MockOAuth2Server()
                mockOAuth2Server.start(8091)
            } catch (e: Exception) {
                println("Failed to start mockserver")
                println(e)
            }
        }

        @AfterAll
        @JvmStatic
        fun cleanup() {
            println("Stopping mockserver")
            mockOAuth2Server.shutdown()
        }
    }

    fun setUpTestApplication(block: suspend ApplicationTestBuilder.() -> Unit) {
        setEnvConfig()
        runMigration()
        clean()

        testApplication {
            behandlingRepository = BehandlingRepositoryPostgres(dataSource)

            every { unleash.isEnabled(any()) } returns true
            val meterRegistry =
                PrometheusMeterRegistry(PrometheusConfig.DEFAULT, PrometheusRegistry.defaultRegistry, Clock.SYSTEM)
            val personRepository = PostgresPersonRepository(dataSource, actionTimer)
            val testKafkaContainer = TestKafkaContainer()
            val paaVegneAvTopic = "paa-vegne-av-topic"
            val overtaBekreftelseKafkaProdusent =
                TestKafkaProducer<PaaVegneAv>(paaVegneAvTopic, testKafkaContainer).producer
            val bekreftelsePåVegneAvKafkaConsumer =
                testKafkaContainer.createConsumer(
                    "bekreftelse-pa-vegne-av-group",
                    LongDeserializer::class,
                    PaaVegneAvAvroDeserializer::class,
                )
            val arbedssøkerperiodeKafkaConsumer =
                testKafkaContainer.createConsumer(
                    "arbedssøkerperiode-group",
                    LongDeserializer::class,
                    PeriodeAvroDeserializer::class,
                )
            val personService =
                PersonService(
                    pdlConnector = pdlConnector,
                    personRepository = personRepository,
                    personObservers = listOf(personObserver),
                    cache = Caffeine.newBuilder().expireAfterWrite(1, TimeUnit.MINUTES).build(),
                )
            val meldepliktMediator =
                MeldepliktMediator(
                    personRepository,
                    personService,
                    listOf(personObserver),
                    meldepliktConnector,
                    actionTimer,
                )

            val arbeidssøkerService = ArbeidssøkerService(arbeidssøkerConnector)
            val arbeidssøkerMediator =
                ArbeidssøkerMediator(
                    arbeidssøkerService,
                    personRepository,
                    personService,
                    listOf(personObserver),
                    actionTimer,
                )
            val arbeidssøkerMottak = ArbeidssøkerMottak(arbeidssøkerMediator, arbeidssøkerperiodeMetrikker)
            val overtakelseMottak = ArbeidssøkerperiodeOvertakelseMottak(arbeidssøkerMediator)
            val kafkaContext =
                KafkaContext(
                    bekreftelsePåVegneAvKafkaProdusent = overtaBekreftelseKafkaProdusent,
                    bekreftelsePåVegneAvKafkaConsumer = bekreftelsePåVegneAvKafkaConsumer,
                    påVegneAvMottak = overtakelseMottak,
                    påVegneAvTopic = paaVegneAvTopic,
                    arbeidssøkerperioderKafkaConsumer = arbedssøkerperiodeKafkaConsumer,
                    arbeidssøkerperioderTopic = "ARBEIDSSOKERPERIODER_TOPIC",
                    arbeidssøkerMottak = arbeidssøkerMottak,
                )

            val personMediator =
                PersonMediator(
                    personRepository,
                    personService,
                    arbeidssøkerMediator,
                    listOf(personObserver),
                    meldepliktMediator,
                    actionTimer,
                    unleash,
                )

            application {
                pluginConfiguration(meterRegistry, kafkaContext)
                internalApi(meterRegistry, arbeidssøkerService)
                personstatusApi(personMediator, synkroniserPersonMetrikker, personService)
                personApi(personService)
                behandlingApi(behandlingRepository)
            }

            block()
        }
    }

    private fun setEnvConfig() {
        System.setProperty("DB_JDBC_URL", "${database.jdbcUrl}&user=${database.username}&password=${database.password}")
        System.setProperty("token-x.client-id", TOKENX_ISSUER_ID)
        System.setProperty("token-x.well-known-url", mockOAuth2Server.wellKnownUrl(TOKENX_ISSUER_ID).toString())
        System.setProperty("azure-app.well-known-url", mockOAuth2Server.wellKnownUrl(AZURE_OPENID_CONFIG_ISSUER).toString())
        System.setProperty("azure-app.client-id", AZURE_APP_CLIENT_ID)
        System.setProperty("GITHUB_SHA", "some_sha")
        System.setProperty("KAFKA_SCHEMA_REGISTRY", "KAFKA_SCHEMA_REGISTRY")
        System.setProperty("KAFKA_SCHEMA_REGISTRY_USER", "KAFKA_SCHEMA_REGISTRY_USER")
        System.setProperty("KAFKA_SCHEMA_REGISTRY_PASSWORD", "KAFKA_SCHEMA_REGISTRY_PASSWORD")
        System.setProperty("KAFKA_BROKERS", "KAFKA_BROKERS")
        System.setProperty("PDL_API_HOST", "pdl-api.test.nais.io")
        System.setProperty("PDL_AUDIENCE", "test:pdl:pdl-api")
    }

    private fun clean() {
        println("Cleaning database")
        using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf(
                    "TRUNCATE TABLE person, hendelse, status_historikk, arbeidssoker, fremtidig_hendelse, arbeidssoker_beslutning",
                ).asExecute,
            )
        }
    }

    fun issueTokenX(ident: String): String =
        mockOAuth2Server
            .issueToken(
                TOKENX_ISSUER_ID,
                "myclient",
                DefaultOAuth2TokenCallback(
                    audience = listOf(REQUIRED_AUDIENCE),
                    claims = mapOf("pid" to ident, "acr" to "Level4"),
                ),
            ).serialize()

    fun issueAzureAdToken(claims: Map<String, Any>): String =
        mockOAuth2Server
            .issueToken(
                audience = AZURE_APP_CLIENT_ID,
                issuerId = AZURE_OPENID_CONFIG_ISSUER,
                claims = claims,
            ).serialize()
}
