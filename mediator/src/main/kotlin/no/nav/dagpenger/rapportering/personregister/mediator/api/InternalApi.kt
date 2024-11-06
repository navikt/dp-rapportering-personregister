package no.nav.dagpenger.rapportering.personregister.mediator.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import no.nav.dagpenger.rapportering.personregister.mediator.connector.ArbeidssøkerregisterConnector

fun Application.internalApi(arbeidssøkerregisterConnector: ArbeidssøkerregisterConnector) {
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
        get("/arbeidssoker") {
            val ident = call.request.queryParameters["ident"]
            if (ident != null) {
                val arbeidssoker = arbeidssøkerregisterConnector.hentSisteArbeidssøkerperiode(ident)
                call.respondText(arbeidssoker)
            } else {
                call.respond(HttpStatusCode.BadRequest, "Mangler ident")
            }
        }
    }
}
