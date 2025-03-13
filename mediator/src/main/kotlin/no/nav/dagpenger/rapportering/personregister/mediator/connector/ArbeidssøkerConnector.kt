package no.nav.dagpenger.rapportering.personregister.mediator.connector

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType.Application
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.Configuration
import no.nav.dagpenger.rapportering.personregister.mediator.Configuration.defaultObjectMapper
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.ActionTimer
import java.net.URI
import kotlin.time.measureTime

class ArbeidssøkerConnector(
    private val arbeidssøkerregisterOppslagUrl: String = Configuration.arbeidssokerregisterOppslagUrl,
    private val oppslagTokenProvider: () -> String? = Configuration.oppslagTokenProvider,
    private val arbeidssokerregisterRecordKeyUrl: String = Configuration.arbeidssokerregisterRecordKeyUrl,
    private val recordKeyTokenProvider: () -> String? = Configuration.recordKeyTokenProvider,
    private val httpClient: HttpClient = createHttpClient(),
    private val actionTimer: ActionTimer,
) {
    suspend fun hentSisteArbeidssøkerperiode(ident: String): List<ArbeidssøkerperiodeResponse> =
        withContext(Dispatchers.IO) {
            val result =
                sendPostRequest(
                    endpointUrl = "$arbeidssøkerregisterOppslagUrl/api/v1/veileder/arbeidssoekerperioder",
                    token = oppslagTokenProvider.invoke() ?: throw RuntimeException("Klarte ikke å hente token"),
                    metrikkNavn = "arbeidssokerregister_hentSisteArbeidssokerperiode",
                    body = ArbeidssøkerperiodeRequestBody(ident),
                    parameters = mapOf("siste" to true),
                ).also {
                    sikkerlogg.info {
                        "Kall til arbeidssøkerregister for å hente arbeidssøkerperiode for $ident ga status ${it.status}"
                    }
                }

            if (result.status != HttpStatusCode.OK) {
                val body = result.bodyAsText()
                sikkerlogg.warn {
                    "Uforventet status ${result.status.value} ved henting av arbeidssøkerperiode for $ident. Response: $body"
                }
                throw RuntimeException("Uforventet status ${result.status.value} ved henting av arbeidssøkerperiode")
            }
            result.body()
        }

    suspend fun hentRecordKey(ident: String): RecordKeyResponse =
        withContext(Dispatchers.IO) {
            val result =
                sendPostRequest(
                    endpointUrl = "$arbeidssokerregisterRecordKeyUrl/api/v1/record-key",
                    token = recordKeyTokenProvider.invoke() ?: throw RuntimeException("Klarte ikke å hente token"),
                    metrikkNavn = "arbeidssokerregister_hentRecordKey",
                    body = RecordKeyRequestBody(ident),
                ).also {
                    sikkerlogg.info {
                        "Kall til arbeidssøkerregister for å hente record key for $ident ga status ${it.status}"
                    }
                }

            if (result.status != HttpStatusCode.OK) {
                val body = result.bodyAsText()
                sikkerlogg.warn {
                    "Uforventet status ${result.status.value} ved henting av record key for $ident. Response: $body"
                }
                throw RuntimeException("Uforventet status ${result.status.value} ved henting av record key")
            }
            result.body()
        }

    private suspend fun sendPostRequest(
        endpointUrl: String,
        token: String,
        metrikkNavn: String,
        body: Any?,
        parameters: Map<String, Any> = emptyMap(),
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

    companion object {
        private val sikkerlogg = KotlinLogging.logger("tjenestekall.HentRapporteringperioder")
    }
}
