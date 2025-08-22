package no.nav.dagpenger.rapportering.personregister.mediator.connector

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.dagpenger.rapportering.personregister.mediator.Configuration
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.ActionTimer

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
                    httpClient = httpClient,
                    actionTimer = actionTimer,
                ).also {
                    logger.info {
                        "Kall til arbeidssøkerregister for å hente arbeidssøkerperiode for ident ga status ${it.status}"
                    }
                }

            if (result.status != HttpStatusCode.OK) {
                logger.warn {
                    "Uforventet status ${result.status.value} ved henting av arbeidssøkerperiode for ident."
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
                    httpClient = httpClient,
                    actionTimer = actionTimer,
                ).also {
                    logger.info {
                        "Kall til arbeidssøkerregister for å hente record key for ident ga status ${it.status}"
                    }
                }

            if (result.status != HttpStatusCode.OK) {
                logger.warn {
                    "Uforventet status ${result.status.value} ved henting av record key for ident."
                }
                throw RuntimeException("Uforventet status ${result.status.value} ved henting av record key")
            }
            result.body()
        }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
