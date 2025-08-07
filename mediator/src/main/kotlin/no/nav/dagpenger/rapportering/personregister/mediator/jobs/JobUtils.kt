package no.nav.dagpenger.rapportering.personregister.mediator.jobs

import com.fasterxml.jackson.annotation.JsonProperty
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.runBlocking
import mu.KLogger
import java.net.InetAddress

data class Leader(
    val name: String,
    @param:JsonProperty("last_update")
    val lastUpdate: String,
)

fun isLeader(
    httpClient: HttpClient,
    logger: KLogger,
): Boolean {
    val hostname = InetAddress.getLocalHost().hostName

    val leader: String =
        try {
            val electorUrl = System.getenv("ELECTOR_GET_URL")
            runBlocking {
                val leaderJson: Leader = httpClient.get(electorUrl).body()
                leaderJson.name
            }
        } catch (e: Exception) {
            logger.error(e) { "Kunne ikke sjekke leader" }
            return true // Det er bedre å få flere pod'er til å starte jobben enn ingen
        }

    return hostname == leader
}
