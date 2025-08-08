package no.nav.dagpenger.rapportering.personregister.mediator.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.api.models.PersonResponse
import no.nav.dagpenger.rapportering.personregister.mediator.connector.PdlConnector
import no.nav.dagpenger.rapportering.personregister.mediator.service.PersonService

private val logger = KotlinLogging.logger {}

internal fun Application.personApi(
    personService: PersonService,
    pdlConnector: PdlConnector,
) {
    routing {
        authenticate("azureAd") {
            route("/person/{personId}") {
                get {
                    logger.info { "GET /person/{personId}" }
                    val personId = call.getPersonId()

                    val ident = personService.hentPersonIdent(personId)
                    if (ident == null) {
                        call.respond(HttpStatusCode.NotFound, "Finner ikke person med personId")
                        return@get
                    }

                    try {
                        pdlConnector
                            .hentPerson(ident)
                            ?.also { person ->
                                call.respond(
                                    HttpStatusCode.OK,
                                    PersonResponse(
                                        ident = ident,
                                        fornavn = person.fornavn,
                                        etternavn = person.etternavn,
                                        mellomnavn = person.mellomnavn,
                                        statsborgerskap = person.statsborgerskap,
                                    ),
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

private fun RoutingCall.getPersonId(): Long =
    this.parameters["personId"]?.toLongOrNull()
        ?: throw IllegalArgumentException("Ugyldig personId")
