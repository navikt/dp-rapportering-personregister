package no.nav.dagpenger.rapportering.personregister.mediator.connector

import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.headers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.Configuration
import no.nav.dagpenger.rapportering.personregister.mediator.Configuration.defaultObjectMapper
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.ActionTimer

class MeldepliktConnector(
    private val meldepliktAdapterUrl: String = Configuration.meldepliktAdatperUrl,
    private val meldepliktAdapterTokenProvider: () -> String? = Configuration.meldepliktAdapterTokenProvider,
    private val httpClient: HttpClient = createHttpClient(),
    private val actionTimer: ActionTimer,
) {
    suspend fun hentMeldeplikt(ident: String): Boolean =
        withContext(Dispatchers.IO) {
            val result =
                sendGetRequest(
                    httpClient = httpClient,
                    endpointUrl = "$meldepliktAdapterUrl/hardpmeldeplikt",
                    token = meldepliktAdapterTokenProvider.invoke() ?: throw RuntimeException("Klarte ikke å hente token"),
                    metrikkNavn = "meldepliktadapter_hentMeldeplikt",
                    headers = mapOf("ident" to ident),
                    actionTimer = actionTimer,
                ).also {
                    logger.info { "Kall til adapter for å hente meldeplikt ga status ${it.status}" }
                }
            if (result.status != HttpStatusCode.OK) {
                logger.warn { "Uforventet status ${result.status.value} ved henting av meldeplikt fra adapter" }
                throw RuntimeException("Uforventet status ${result.status.value} ved henting av meldeplikt fra adapter")
            }
            val body = result.bodyAsText()
            logger.info("Body: $body")
            defaultObjectMapper.readValue<Boolean>(body)
        }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}

data class MeldepliktRequestBody(
    val ident: String,
)
