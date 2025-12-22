package no.nav.dagpenger.rapportering.personregister.mediator

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.natpryce.konfig.ConfigurationMap
import com.natpryce.konfig.ConfigurationProperties
import com.natpryce.konfig.EnvironmentVariables
import com.natpryce.konfig.Key
import com.natpryce.konfig.overriding
import com.natpryce.konfig.stringType
import io.getunleash.DefaultUnleash
import io.getunleash.util.UnleashConfig
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.oauth2.CachedOauth2Client
import no.nav.dagpenger.oauth2.OAuth2Config.AzureAd
import no.nav.dagpenger.rapportering.personregister.kafka.KafkaSchemaRegistryConfig
import no.nav.dagpenger.rapportering.personregister.kafka.KafkaServerKonfigurasjon
import java.time.ZoneId
import java.util.UUID

val ZONE_ID: ZoneId = ZoneId.of("Europe/Oslo")

internal object Configuration {
    const val APP_NAME = "dp-rapportering-personregister"

    private val defaultProperties =
        ConfigurationMap(
            mapOf(
                "RAPID_APP_NAME" to APP_NAME,
                "KAFKA_CONSUMER_GROUP_ID" to "dp-rapportering-personregister-v1",
                "KAFKA_RAPID_TOPIC" to "teamdagpenger.rapid.v1",
                "KAFKA_RESET_POLICY" to "earliest",
            ),
        )

    val properties =
        ConfigurationProperties.systemProperties() overriding EnvironmentVariables() overriding defaultProperties

    val config: Map<String, String> =
        properties.list().reversed().fold(emptyMap()) { map, pair ->
            map + pair.second
        }

    val arbeidssokerregisterOppslagUrl by lazy {
        properties[Key("ARBEIDSSOKERREGISTER_OPPSLAG_HOST", stringType)].formatUrl()
    }
    val arbeidssokerregisterRecordKeyUrl by lazy {
        properties[Key("ARBEIDSSOKERREGISTER_RECORD_KEY_HOST", stringType)].formatUrl()
    }

    val meldepliktAdatperUrl by lazy {
        properties[Key("MELDEPLIKT_ADAPTER_HOST", stringType)].formatUrl()
    }

    val meldekortregisterUrl by lazy {
        properties[Key("MELDEKORTREGISTER_HOST", stringType)].formatUrl()
    }

    private val azureAdConfig by lazy { AzureAd(properties) }
    private val azureAdClient by lazy {
        CachedOauth2Client(
            tokenEndpointUrl = azureAdConfig.tokenEndpointUrl,
            authType = azureAdConfig.clientSecret(),
        )
    }
    val oppslagTokenProvider: () -> String by lazy {
        {
            runBlocking {
                azureAdClient
                    .clientCredentials(properties[Key("ARBEIDSSOKERREGISTER_OPPSLAG_SCOPE", stringType)])
                    .access_token ?: throw RuntimeException("Failed to get token")
            }
        }
    }
    val recordKeyTokenProvider: () -> String by lazy {
        {
            runBlocking {
                azureAdClient
                    .clientCredentials(properties[Key("ARBEIDSSOKERREGISTER_RECORD_KEY_SCOPE", stringType)])
                    .access_token ?: throw RuntimeException("Failed to get token")
            }
        }
    }
    val meldepliktAdapterTokenProvider: () -> String by lazy {
        {
            runBlocking {
                azureAdClient
                    .clientCredentials(properties[Key("MELDEPLIKT_ADAPTER_SCOPE", stringType)])
                    .access_token ?: throw RuntimeException("Failed to get token")
            }
        }
    }
    val meldekortregisterTokenProvider: () -> String by lazy {
        {
            runBlocking {
                azureAdClient
                    .clientCredentials(properties[Key("MELDEKORTREGISTER_SCOPE", stringType)])
                    .access_token ?: throw RuntimeException("Failed to get token")
            }
        }
    }
    val pdlApiTokenProvider: () -> String by lazy {
        {
            runBlocking {
                azureAdClient
                    .clientCredentials(properties[Key("PDL_API_SCOPE", stringType)])
                    .access_token ?: throw RuntimeException("Failed to get token")
            }
        }
    }

    val pdlUrl by lazy {
        properties[Key("PDL_API_HOST", stringType)].let {
            "https://$it/graphql"
        }
    }

    val kafkaSchemaRegistryConfig =
        KafkaSchemaRegistryConfig(
            url = properties[Key("KAFKA_SCHEMA_REGISTRY", stringType)],
            username = properties[Key("KAFKA_SCHEMA_REGISTRY_USER", stringType)],
            password = properties[Key("KAFKA_SCHEMA_REGISTRY_PASSWORD", stringType)],
            autoRegisterSchema = true,
            avroSpecificReaderConfig = true,
        )

    val kafkaServerKonfigurasjon =
        KafkaServerKonfigurasjon(
            autentisering = "SSL",
            kafkaBrokers = properties[Key("KAFKA_BROKERS", stringType)],
            keystorePath = properties.getOrNull(Key("KAFKA_KEYSTORE_PATH", stringType)),
            credstorePassword = properties.getOrNull(Key("KAFKA_CREDSTORE_PASSWORD", stringType)),
            truststorePath = properties.getOrNull(Key("KAFKA_TRUSTSTORE_PATH", stringType)),
        )

    val defaultObjectMapper: ObjectMapper =
        ObjectMapper().apply {
            registerModule(JavaTimeModule())
            registerModule(
                KotlinModule
                    .Builder()
                    .configure(KotlinFeature.NullToEmptyCollection, true)
                    .configure(KotlinFeature.NullToEmptyMap, true)
                    .configure(KotlinFeature.NullIsSameAsDefault, true)
                    .configure(KotlinFeature.SingletonSupport, true)
                    .configure(KotlinFeature.StrictNullChecks, false)
                    .build(),
            )
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        }

    private val unleashConfig by lazy {
        UnleashConfig
            .builder()
            .fetchTogglesInterval(5)
            .appName(properties.getOrElse(Key("NAIS_APP_NAME", stringType), UUID.randomUUID().toString()))
            .instanceId(properties.getOrElse(Key("NAIS_CLIENT_ID", stringType), UUID.randomUUID().toString()))
            .unleashAPI(properties[Key("UNLEASH_SERVER_API_URL", stringType)] + "/api")
            .apiKey(properties[Key("UNLEASH_SERVER_API_TOKEN", stringType)])
            .environment(properties[Key("UNLEASH_SERVER_API_ENV", stringType)])
            .build()
    }

    val unleash by lazy {
        DefaultUnleash(unleashConfig)
    }
}

private fun String.formatUrl(): String = if (this.startsWith("http")) this else "https://$this"
