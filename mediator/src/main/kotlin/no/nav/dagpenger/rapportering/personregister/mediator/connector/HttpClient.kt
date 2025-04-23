package no.nav.dagpenger.rapportering.personregister.mediator.connector

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType.Application
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.serialization.jackson.jackson
import no.nav.dagpenger.rapportering.personregister.mediator.Configuration.defaultObjectMapper
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.ActionTimer
import java.net.URI
import java.time.Duration
import kotlin.time.measureTime

fun createHttpClient(engine: HttpClientEngine = CIO.create {}) =
    HttpClient(engine) {
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

suspend fun sendPostRequest(
    httpClient: HttpClient,
    endpointUrl: String,
    token: String,
    metrikkNavn: String,
    body: Any?,
    parameters: Map<String, Any> = emptyMap(),
    actionTimer: ActionTimer,
): HttpResponse {
    val response: HttpResponse
    val tidBrukt =
        measureTime {
            response =
                httpClient.post(URI(endpointUrl).toURL()) {
                    bearerAuth(token)
                    contentType(Application.Json)
                    setBody(defaultObjectMapper.writeValueAsString(body))
                    parameters.forEach { (key, value) -> parameter(key, value) }
                }
        }
    actionTimer.httpTimer(metrikkNavn, response.status, HttpMethod.Post, tidBrukt.inWholeSeconds)
    return response
}

suspend fun sendGetRequest(
    httpClient: HttpClient,
    endpointUrl: String,
    token: String,
    metrikkNavn: String,
    parameters: Map<String, Any> = emptyMap(),
    headers: Map<String, Any> = emptyMap(),
    actionTimer: ActionTimer,
): HttpResponse {
    val response: HttpResponse
    val tidBrukt =
        measureTime {
            response =
                httpClient.get(URI(endpointUrl).toURL()) {
                    bearerAuth(token)
                    contentType(Application.Json)
                    parameters.forEach { (key, value) -> parameter(key, value) }
                    headers.forEach { (key, value) -> header(key, value) }
                }
        }
    actionTimer.httpTimer(metrikkNavn, response.status, HttpMethod.Get, tidBrukt.inWholeSeconds)
    return response
}
