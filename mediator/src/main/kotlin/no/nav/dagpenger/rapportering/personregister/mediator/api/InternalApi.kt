package no.nav.dagpenger.rapportering.personregister.mediator.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.dagpenger.rapportering.personregister.mediator.PersonMediator
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.VedtakHendelse

fun Application.internalApi(
    meterRegistry: PrometheusMeterRegistry,
    personMediator: PersonMediator,
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
        post("/rydd") {
            val body = call.receive<VedtakHendelse>()

            personMediator.behandle(body)
        }
    }
}
