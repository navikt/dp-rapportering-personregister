package no.nav.dagpenger.rapportering.personregister.mediator.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.service.PersonService

private val logger = KotlinLogging.logger {}

internal fun Application.personApi(personService: PersonService) {
    routing {
        authenticate("azureAd") {
            route("/person") {
                post {
                    logger.info { "POST /person" }
                    val ident = call.receive<String>()

                    if (!ident.matches(Regex("[0-9]{11}"))) {
                        logger.error("Person-ident må ha 11 sifre")
                        call.respond(HttpStatusCode.BadRequest, "Person-ident må ha 11 sifre")
                        return@post
                    }

                    try {
                        personService
                            .hentPersonId(ident)
                            ?.also { personId ->
                                call.respond(
                                    HttpStatusCode.OK,
                                    personId,
                                )
                            }
                            ?: call.respond(HttpStatusCode.NotFound, "Finner ikke person")
                    } catch (e: Exception) {
                        logger.error(e) { "Kunne ikke hente person" }
                        call.respond(HttpStatusCode.InternalServerError, "Kunne ikke hente person")
                    }
                }
            }
        }
    }
}
