package no.nav.dagpenger.rapportering.personregister.mediator.connector

import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.Configuration
import no.nav.dagpenger.rapportering.personregister.mediator.Configuration.defaultObjectMapper
import java.net.URI

class ArbeidssøkerConnector(
    private val arbeidssøkerregisterUrl: String = Configuration.arbeidssoekerregisterUrl,
    private val tokenProvider: () -> String? = Configuration.tokenProvider,
    private val httpClient: HttpClient = createHttpClient(),
) {
    suspend fun hentSisteArbeidssøkerperiode(ident: String) =
        withContext(Dispatchers.IO) {
            val token = tokenProvider.invoke() ?: throw RuntimeException("Klarte ikke å hente token")
            val result =
                httpClient
                    .post(URI("$arbeidssøkerregisterUrl/api/v1/veileder/arbeidssoekerperioder").toURL()) {
                        bearerAuth(token)
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
