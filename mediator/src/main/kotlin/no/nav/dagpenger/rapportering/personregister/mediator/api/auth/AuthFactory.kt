package no.nav.dagpenger.rapportering.personregister.mediator.api.auth

import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.natpryce.konfig.PropertyGroup
import com.natpryce.konfig.getValue
import com.natpryce.konfig.stringType
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.jackson.jackson
import io.ktor.server.auth.jwt.JWTAuthenticationProvider
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.rapportering.personregister.mediator.Configuration
import java.net.URI
import java.net.URL
import java.util.concurrent.TimeUnit

object AuthFactory {
    @Suppress("ClassName")
    private object token_x : PropertyGroup() {
        val well_known_url by stringType
        val client_id by stringType
    }

    private val tokenXConfiguration: OpenIdConfiguration by lazy {
        runBlocking {
            httpClient.get(Configuration.properties[token_x.well_known_url]).body()
        }
    }

    enum class Issuer {
        TokenX,
    }

    fun issuerFromString(issuer: String?) =
        when (issuer) {
            tokenXConfiguration.issuer -> Issuer.TokenX
            else -> {
                throw IllegalArgumentException("Ikke støttet issuer: $issuer")
            }
        }

    fun JWTAuthenticationProvider.Config.tokenX() {
        verifier(jwkProvider(URI(tokenXConfiguration.jwksUri).toURL()), tokenXConfiguration.issuer) {
            withAudience(Configuration.properties[token_x.client_id])
        }
        realm = Configuration.APP_NAME
        validate { credentials ->
            validator(credentials)
        }
    }

    private fun jwkProvider(url: URL) =
        JwkProviderBuilder(url)
            .cached(10, 24, TimeUnit.HOURS) // cache up to 10 JWKs for 24 hours
            .rateLimited(
                10,
                1,
                TimeUnit.MINUTES,
            ) // if not cached, only allow max 10 different keys per minute to be fetched from external provider
            .build()
}

private data class OpenIdConfiguration(
    @JsonProperty("jwks_uri")
    val jwksUri: String,
    @JsonProperty("issuer")
    val issuer: String,
    @JsonProperty("token_endpoint")
    val tokenEndpoint: String,
    @JsonProperty("authorization_endpoint")
    val authorizationEndpoint: String,
)

private val httpClient =
    HttpClient(CIO) {
        install(ContentNegotiation) {
            jackson {
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
    }
