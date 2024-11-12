package no.nav.dagpenger.rapportering.personregister.mediator.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.dagpenger.rapportering.personregister.mediator.Configuration.defaultObjectMapper
import no.nav.dagpenger.rapportering.personregister.mediator.connector.ArbeidssøkerConnector

fun Application.internalApi(
    arbeidssøkerConnector: ArbeidssøkerConnector,
    meterRegistry: PrometheusMeterRegistry,
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
        get("/arbeidssoker") {
            val ident = call.request.queryParameters["ident"]
            if (ident != null) {
                val arbeidssoker = arbeidssøkerConnector.hentSisteArbeidssøkerperiode(ident)
                call.respondText(defaultObjectMapper.writeValueAsString(arbeidssoker))
            } else {
                call.respond(HttpStatusCode.BadRequest, "Mangler ident")
            }
        }
    }
}
