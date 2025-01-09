package no.nav.dagpenger.rapportering.personregister.mediator.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

private val httpClient = HttpClient(CIO)

suspend fun fetchLeaderName(url: String): String? =
    try {
        val response = httpClient.get(url).bodyAsText()
        val jsonObject = Json.parseToJsonElement(response).jsonObject
        jsonObject["name"]?.jsonPrimitive?.content
    } catch (e: Exception) {
        logger.error(e) { "Error fetching or parsing JSON from $url" }
        null
    }

object Leader {
    private suspend fun getLeader(): String? {
        val electorUrl = System.getenv("ELECTOR_GET_URL")
        if (electorUrl.isBlank()) {
            logger.error { "Environment variable ELECTOR_GET_URL is not set or is blank." }
            return null
        }

        logger.info { "Fetching leader information from URL: $electorUrl" }
        return fetchLeaderName(electorUrl)?.also {
            logger.info { "Leader name fetched successfully: $it" }
        }
    }

    suspend fun isLeader(): Boolean {
        val leaderJson = getLeader()
        if (leaderJson.isNullOrBlank()) {
            logger.warn { "Leader name is null or blank. Assuming not the leader." }
            return false
        }

        // TODO: Add your own logic to determine actual leadership
        logger.info { "Assuming current instance is the leader (demo purposes)." }
        return true
    }
}

fun main() {
    runBlocking {
        val isLeader = Leader.isLeader()
        println("Is this instance the leader? $isLeader")
    }
}
