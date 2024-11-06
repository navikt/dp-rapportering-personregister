package no.nav.dagpenger.rapportering.personregister.mediator.connector

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.jackson.jackson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.Configuration
import no.nav.dagpenger.rapportering.personregister.mediator.Configuration.defaultObjectMapper
import java.net.URI
import java.time.Duration

class ArbeidssøkerregisterConnector(
    private val arbeidssøkerregisterUrl: String = Configuration.arbeidssoekerregisterUrl,
    private val tokenProvider: () -> String? = Configuration.tokenProvider,
) {
    private val httpClient = createHttpClient()

    suspend fun hentSisteArbeidssøkerperiode(ident: String) =
        withContext(Dispatchers.IO) {
            val result =
                httpClient
                    .post(URI("$arbeidssøkerregisterUrl/api/v1/arbeidssoekerperioder").toURL()) {
                        header(HttpHeaders.Authorization, "Bearer ${tokenProvider()}")
                        contentType(ContentType.Application.Json)
                        parameter("siste", true)
                        setBody(defaultObjectMapper.writeValueAsString(ArbeidssøkerperiodeRequestBody(ident)))
                    }.also {
                        logger.info { "Kall til arbeidssøkerregister for å hente profilering ga status ${it.status}" }
                        sikkerlogg.info { "Kall til arbeidssøkerregister for å hente profilering for $ident ga status ${it.status}" }
                    }

            val body = result.bodyAsText()
            if (result.status != HttpStatusCode.OK) {
                logger.warn { "Uforventet HTTP status ${result.status.value} ved henting av profilering" }
                sikkerlogg.warn { "Uforventet HTTP status ${result.status.value} ved henting av profilering for $ident. Response: $body" }
            }
            body
        }

    companion object {
        private val logger = KotlinLogging.logger {}
        val sikkerlogg = KotlinLogging.logger("tjenestekall.HentRapporteringperioder")
    }
}

data class ArbeidssøkerperiodeRequestBody(
    val identitetsnummer: String,
)

fun createHttpClient() =
    HttpClient(CIO.create {}) {
        expectSuccess = false

        install(HttpTimeout) {
            connectTimeoutMillis = Duration.ofSeconds(60).toMillis()
            requestTimeoutMillis = Duration.ofSeconds(60).toMillis()
            socketTimeoutMillis = Duration.ofSeconds(60).toMillis()
        }

        install(ContentNegotiation) {
            jackson {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
    }
