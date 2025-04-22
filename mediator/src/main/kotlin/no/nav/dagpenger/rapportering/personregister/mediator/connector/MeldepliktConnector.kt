package no.nav.dagpenger.rapportering.personregister.mediator.connector

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.Configuration
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
                sendPostRequest(
                    httpClient = httpClient,
                    endpointUrl = "$meldepliktAdapterUrl/hardpmeldeplikt",
                    token = meldepliktAdapterTokenProvider.invoke() ?: throw RuntimeException("Klarte ikke å hente token"),
                    metrikkNavn = "meldepliktadapter_hentMeldeplikt",
                    body = MeldepliktRequestBody(ident),
                    actionTimer = actionTimer,
                ).also {
                    logger.info { "Kall til adapter for å hente meldeplikt ga status ${it.status}" }
                }
            if (result.status != HttpStatusCode.OK) {
                logger.warn { "Uforventet status ${result.status.value} ved henting av meldeplikt fra adapter" }
                throw RuntimeException("Uforventet status ${result.status.value} ved henting av meldeplikt fra adapter")
            }
            result.body()
        }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}

data class MeldepliktRequestBody(
    val ident: String,
)
