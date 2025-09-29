package no.nav.dagpenger.rapportering.personregister.mediator.api

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.dagpenger.rapportering.personregister.mediator.service.ArbeidssøkerService

private val logger = KotlinLogging.logger {}

fun Application.internalApi(
    meterRegistry: PrometheusMeterRegistry,
    arbeidssøkerService: ArbeidssøkerService,
) {
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
        post("/sjekk") {
            logger.info { "POST /sjekk" }
            val request = call.receive<IdentBody>()
            logger.info { "Henter siste arbeidssøkerperiode" }
            val result = arbeidssøkerService.hentSisteArbeidssøkerperiode(request.ident)
            logger.info { "Siste periode er ${result?.periodeId}" }
            logger.info { "Arbeidssøkperiode: $result" }
            call.respond(HttpStatusCode.OK)
        }
    }
}
