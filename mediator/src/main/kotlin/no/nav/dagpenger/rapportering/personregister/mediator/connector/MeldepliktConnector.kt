package no.nav.dagpenger.rapportering.personregister.mediator.connector

import com.fasterxml.jackson.module.kotlin.readValue
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.dagpenger.rapportering.personregister.mediator.Configuration
import no.nav.dagpenger.rapportering.personregister.mediator.Configuration.defaultObjectMapper
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.ActionTimer
import no.nav.dagpenger.rapportering.personregister.modell.meldestatus.MeldestatusRequest
import no.nav.dagpenger.rapportering.personregister.modell.meldestatus.MeldestatusResponse
import java.time.LocalDate

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
                    token =
                        meldepliktAdapterTokenProvider.invoke()
                            ?: throw RuntimeException("Klarte ikke å hente token"),
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

            defaultObjectMapper.readValue<Boolean>(result.bodyAsText())
        }

    suspend fun hentMeldestatus(
        arenaPersonId: Long? = null,
        ident: String? = null,
        dato: LocalDate? = null,
    ): MeldestatusResponse =
        withContext(Dispatchers.IO) {
            val result =
                sendPostRequest(
                    httpClient = httpClient,
                    endpointUrl = "$meldepliktAdapterUrl/meldestatus",
                    token =
                        meldepliktAdapterTokenProvider.invoke()
                            ?: throw RuntimeException("Klarte ikke å hente token"),
                    metrikkNavn = "meldepliktadapter_hentMeldestatus",
                    body = MeldestatusRequest(arenaPersonId = arenaPersonId, personident = ident, sokeDato = dato),
                    parameters = mapOf(),
                    actionTimer = actionTimer,
                ).also {
                    logger.info { "Kall til adapter for å hente meldestatus ga status ${it.status}" }
                }
            if (result.status != HttpStatusCode.OK) {
                logger.warn { "Uforventet status ${result.status.value} ved henting av meldestatus fra adapter" }
                throw RuntimeException("Uforventet status ${result.status.value} ved henting av meldestatus fra adapter")
            }

            defaultObjectMapper.readValue<MeldestatusResponse>(result.bodyAsText())
        }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
