package no.nav.dagpenger.rapportering.personregister.mediator.api

import com.github.navikt.tbd_libs.kafka.AivenConfig
import com.github.navikt.tbd_libs.kafka.ConsumerProducerFactory
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import mu.KotlinLogging
import org.apache.kafka.clients.producer.ProducerRecord

data class KafkaMessage(
    val key: String,
    val value: String,
)

private val logger = KotlinLogging.logger {}

fun Application.internalApi(meterRegistry: PrometheusMeterRegistry) {
    routing {
        get("/") {
            call.respond(HttpStatusCode.OK)
        }
        get("/isAlive") {
            call.respondText("Alive")
        }
        get("/isReady") {
            call.respondText("Ready")
        }
        get("/metrics") {
            call.respondText(meterRegistry.scrape())
        }

        post("/test") {
            logger.info { "Mottok melding som skal sendes" }
            try {
                val body = call.receive<KafkaMessage>()

                val factory = ConsumerProducerFactory(AivenConfig.default)
                val produsent = factory.createProducer()

                produsent.send(ProducerRecord("teamdagpenger.test-topic", body.key, body.value))

                logger.info { "Produserte melding med key: ${body.key} og value: ${body.value}" }
                call.respond(HttpStatusCode.OK)
            } catch (e: Exception) {
                logger.error(e) { "Failed to process message" }
                call.respond(HttpStatusCode.InternalServerError)
            }
            call.respond(HttpStatusCode.OK, "Return etter try")
        }
    }
}
