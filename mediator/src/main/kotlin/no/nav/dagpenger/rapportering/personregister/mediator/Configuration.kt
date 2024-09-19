package no.nav.dagpenger.rapportering.personregister.mediator

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.natpryce.konfig.ConfigurationMap
import com.natpryce.konfig.ConfigurationProperties
import com.natpryce.konfig.EnvironmentVariables
import com.natpryce.konfig.getValue
import com.natpryce.konfig.overriding

internal object Configuration {
    const val APP_NAME = "dp-rapportering-personregister"
    private val defaultProperties =
        ConfigurationMap(
            mapOf(
                "RAPID_APP_NAME" to APP_NAME,
                "KAFKA_CONSUMER_GROUP_ID" to "dp-rapportering-personregister-v1",
                "KAFKA_RAPID_TOPIC" to "teamdagpenger.rapid.v1",
                "KAFKA_RESET_POLICY" to "latest",
                // "Grupper.saksbehandler" to "123",
            ),
        )

    /*object Grupper : PropertyGroup() {
        val saksbehandler by stringType
    }*/

    val properties =
        ConfigurationProperties.systemProperties() overriding EnvironmentVariables() overriding defaultProperties

    val config: Map<String, String> =
        properties.list().reversed().fold(emptyMap()) { map, pair ->
            map + pair.second
        }

    /*private val azureAd by lazy {
        val aad = OAuth2Config.AzureAd(properties)
        CachedOauth2Client(
            tokenEndpointUrl = aad.tokenEndpointUrl,
            authType = aad.clientSecret(),
        )
    }

    fun azureADClient() =
        { audience: String ->
            azureAd
                .clientCredentials(audience)
                .accessToken
        }*/

    val defaultObjectMapper: ObjectMapper =
        ObjectMapper()
            .registerKotlinModule()
            .registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
}
