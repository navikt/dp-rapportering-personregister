package no.nav.dagpenger.rapportering.personregister.mediator.connector

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.dagpenger.rapportering.personregister.mediator.Configuration
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.ActionTimer
import java.time.LocalDateTime

class MeldekortregisterConnector(
    private val meldekortregisterUrl: String = Configuration.meldekortregisterUrl,
    private val meldekortregisterTokenProvider: () -> String? = Configuration.meldekortregisterTokenProvider,
    private val httpClient: HttpClient = createHttpClient(),
    private val actionTimer: ActionTimer,
) {
    suspend fun oppdaterIdent(
        personId: Long,
        ident: String,
        nyIdent: String,
    ) = withContext(Dispatchers.IO) {
        val result =
            sendPostRequest(
                httpClient = httpClient,
                endpointUrl = "$meldekortregisterUrl/oppdater_ident",
                token =
                    meldekortregisterTokenProvider.invoke()
                        ?: throw RuntimeException("Klarte ikke å hente token"),
                metrikkNavn = "meldekortregister_oppdaterIdent",
                body = OppdaterIdentRequest(personId, ident, nyIdent),
                parameters = mapOf(),
                actionTimer = actionTimer,
            ).also {
                logger.info { "Kall til meldekortregister for å oppdatere ident ga status ${it.status}" }
            }

        if (result.status != HttpStatusCode.OK) {
            logger.error { "Uforventet status ${result.status.value} ved oppdatering av ident i meldekortregister" }
            sikkerLogg.error { "Uforventet status ved oppdatering av ident i meldekortregister. Response: $result" }
            throw RuntimeException("Uforventet status ${result.status.value} ved oppdatering av ident i meldekortregister")
        }
    }

    suspend fun konsoliderIdenter(
        personId: Long,
        gjeldendeIdent: String,
        identer: List<String>,
    ) = withContext(Dispatchers.IO) {
        val result =
            sendPostRequest(
                httpClient = httpClient,
                endpointUrl = "$meldekortregisterUrl/konsolider_identer",
                token =
                    meldekortregisterTokenProvider.invoke()
                        ?: throw RuntimeException("Klarte ikke å hente token"),
                metrikkNavn = "meldekortregister_konsoliderIdenter",
                body = KonsoliderIdenterRequest(personId, gjeldendeIdent, identer),
                parameters = mapOf(),
                actionTimer = actionTimer,
            ).also {
                logger.info { "Kall til meldekortregister for å konsolidere identer ga status ${it.status}" }
            }

        if (result.status != HttpStatusCode.OK) {
            logger.error { "Uforventet status ${result.status.value} ved konsolidering av identer i meldekortregister" }
            sikkerLogg.error { "Uforventet status ved konsolidering av identer i meldekortregister. Response: $result" }
            throw RuntimeException("Uforventet status ${result.status.value} ved konsolidering av identer i meldekortregister")
        }
    }

    suspend fun hentSisteInnsendteMeldekort(ident: String): InnsendtMeldekortResponse? =
        withContext(Dispatchers.IO) {
            val response =
                sendGetRequest(
                    httpClient = httpClient,
                    endpointUrl = "$meldekortregisterUrl/meldekort",
                    token =
                        meldekortregisterTokenProvider.invoke()
                            ?: throw RuntimeException("Klarte ikke å hente token"),
                    metrikkNavn = "meldekortregister_hentSisteInnsendteMeldekort",
                    parameters = mapOf("ident" to ident, "status" to "Innsendt"),
                    headers = mapOf(),
                    actionTimer = actionTimer,
                ).also {
                    logger.info { "Kall til meldekortregister for å hente siste innsendte meldekort ga status ${it.status}" }
                }

            when (response.status) {
                HttpStatusCode.OK -> {
                    response
                        .body<List<InnsendtMeldekortResponse>>()
                        .maxByOrNull { it.innsendtTidspunkt ?: LocalDateTime.MIN }
                }

                HttpStatusCode.NotFound -> {
                    null
                }

                else -> {
                    val body = response.bodyAsText()
                    logger.error { "Uforventet status ${response.status.value} ved henting av siste innsendte meldekort i meldekortregister" }
                    sikkerLogg.error { "Uforventet status ved henting av siste innsendte meldekort i meldekortregister. Response: $body" }
                    throw RuntimeException(
                        "Uforventet status ${response.status.value} ved henting av siste innsendte meldekort i meldekortregister",
                    )
                }
            }
        }

    companion object {
        private val logger = KotlinLogging.logger {}
        private val sikkerLogg = KotlinLogging.logger("tjenestekall")
    }

    data class OppdaterIdentRequest(
        val personId: Long,
        val ident: String,
        val nyIdent: String,
    )

    data class KonsoliderIdenterRequest(
        val personId: Long,
        val gjeldendeIdent: String,
        val identer: List<String>,
    )
}
