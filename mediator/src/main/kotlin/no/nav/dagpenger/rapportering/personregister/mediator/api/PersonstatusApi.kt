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
import no.nav.dagpenger.rapportering.personregister.api.models.PersonResponse
import no.nav.dagpenger.rapportering.personregister.api.models.StatusResponse
import no.nav.dagpenger.rapportering.personregister.mediator.api.auth.ident
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.service.ArbeidssøkerService

private val logger = KotlinLogging.logger {}

internal fun Application.personstatusApi(
    personRepository: PersonRepository,
    arbeidssøkerService: ArbeidssøkerService,
) {
    routing {
        authenticate("tokenX") {
            route("/personstatus") {
                get {
                    logger.info { "GET /personstatus" }
                    val ident = call.ident()
                    personRepository
                        .hentPerson(ident)
                        ?.also {
                            call.respond(
                                HttpStatusCode.OK,
                                PersonResponse(
                                    ident = it.ident,
                                    status = StatusResponse.valueOf(it.status.type.name),
                                ),
                            )
                        }
                        ?: call.respond(HttpStatusCode.NotFound, "Finner ikke status for person")
                }
            }
            route("/test") {
                get {
                    logger.info { "GET /test" }
                    val ident = call.ident()
                    arbeidssøkerService.sendArbeidssøkerBehov(ident)
                    call.respond(HttpStatusCode.OK, "Hello, world!")
                }
            }
        }
    }
}
