package no.nav.dagpenger.rapportering.personregister.mediator.api

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.connector.createHttpClient

private val logger = KotlinLogging.logger {}

suspend fun getJSONFromUrl(url: String): String {
    val client = createHttpClient()
    return client.use { it.get(url).bodyAsText() } // Properly close the client after use
}

object Leader {
    private suspend fun getLeader(): String? {
        val electorUrl = System.getenv("ELECTOR_GET_URL")
        if (electorUrl.isNullOrBlank()) {
            logger.error { "Environment variable ELECTOR_GET_URL is not set or is blank." }
            return null
        }

        return try {
            logger.info { "Fetching leader information from URL: $electorUrl" }
            val leaderJson = getJSONFromUrl(electorUrl)
            logger.info { "Leader JSON fetched successfully: $leaderJson" }
            leaderJson
        } catch (e: Exception) {
            logger.error(e) { "Failed to fetch leader JSON from $electorUrl" }
            null
        }
    }

    suspend fun isLeader(): Boolean {
        val leaderJson = getLeader()
        if (leaderJson.isNullOrBlank()) {
            logger.warn { "Leader JSON is null or blank. Assuming not the leader." }
            return false
        }

        // TODO: Add actual logic to determine if this instance is the leader
        logger.info { "Assuming current instance is the leader for demonstration purposes." }
        return true
    }
}
