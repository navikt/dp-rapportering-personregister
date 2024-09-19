package no.nav.dagpenger.rapportering.personregister.mediator.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.api.auth.ident
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository

private val logger = KotlinLogging.logger {}

internal fun Application.personstatusApi(personRepository: PersonRepository) {
    routing {
        authenticate("tokenX") {
            route("/personstatus") {
                get {
                    logger.info { "GET /personstatus" }
                    val ident = call.ident()
                    personRepository
                        .finn(ident)
                        ?.also { call.respond(HttpStatusCode.OK, it) }
                        ?: call.respond(HttpStatusCode.NotFound)
                }
            }
        }
    }
}
