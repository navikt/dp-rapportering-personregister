package no.nav.dagpenger.rapportering.personregister.mediator.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.service.PersonService

private val logger = KotlinLogging.logger {}

internal fun Application.personApi(personService: PersonService) {
    routing {
        authenticate("azureAd") {
            route("/hentPersonId") {
                post {
                    logger.info { "POST /hentPersonId" }
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

            route("/hentIdent") {
                post {
                    logger.info { "POST /hentIdent" }

                    try {
                        val personId = call.receive<String>().toLong()

                        personService
                            .hentIdent(personId)
                            ?.also { ident ->
                                call.respond(
                                    HttpStatusCode.OK,
                                    ident,
                                )
                            }
                            ?: call.respond(HttpStatusCode.NotFound, "Finner ikke person")
                    } catch (e: NumberFormatException) {
                        logger.error(e) { "Ikke gyldig personId" }
                        call.respond(HttpStatusCode.BadRequest, "Ikke gyldig personId")
                    } catch (e: Exception) {
                        logger.error(e) { "Kunne ikke hente person" }
                        call.respond(HttpStatusCode.InternalServerError, "Kunne ikke hente person")
                    }
                }
            }

            route("/frasiAnsvar") {
                post {
                    logger.info { "POST /frasiAnsvar" }
                    val identer = call.receive<List<String>>()

                    identer.forEach { ident ->
                        val person = personService.hentPerson(ident)
                        person?.observers?.forEach { it.sendFrasigelsesmelding(person, true) }
                    }

                    call.respond(HttpStatusCode.OK)
                }
            }
        }
    }
}
