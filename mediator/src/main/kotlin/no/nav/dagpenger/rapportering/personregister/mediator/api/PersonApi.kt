package no.nav.dagpenger.rapportering.personregister.mediator.api

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import no.nav.dagpenger.rapportering.personregister.mediator.service.PersonService
import java.net.URI

private val logger = KotlinLogging.logger {}

internal fun Application.personApi(personService: PersonService) {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            when (cause) {
                is PersonNotFoundException -> {
                    logger.warn { "Finner ikke person" }
                    call.respond(
                        HttpStatusCode.NotFound,
                        HttpProblem(
                            title = "Not Found",
                            status = 404,
                            detail = cause.message,
                        ),
                    )
                }

                is BadRequestException -> {
                    logger.error { "Bad Request: ${cause.message}" }
                    call.respond(
                        HttpStatusCode.BadRequest,
                        HttpProblem(
                            title = "Bad Request",
                            status = 400,
                            detail = cause.message,
                        ),
                    )
                }

                else -> {
                    logger.error(cause) { "Kunne ikke hente person" }
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        HttpProblem(
                            title = "Internal Server Error",
                            status = 500,
                            detail = cause.message,
                        ),
                    )
                }
            }
        }
    }
    routing {
        authenticate("azureAd") {
            route("/hentPersonId") {
                post {
                    logger.info { "POST /hentPersonId" }
                    val request = call.receive<IdentBody>()

                    if (!request.ident.matches(Regex("[0-9]{11}"))) {
                        logger.error { "Person-ident må ha 11 sifre" }
                        throw BadRequestException("Person-ident må ha 11 sifre")
                    }

                    personService
                        .hentPersonId(request.ident)
                        ?.also { personId ->
                            call.respond(
                                HttpStatusCode.OK,
                                PersonIdBody(personId),
                            )
                        }
                        ?: throw PersonNotFoundException()
                }
            }

            route("/hentIdent") {
                post {
                    logger.info { "POST /hentIdent" }

                    try {
                        val request = call.receive<PersonIdBody>()

                        personService
                            .hentIdent(request.personId)
                            ?.also { ident ->
                                call.respond(
                                    HttpStatusCode.OK,
                                    IdentBody(ident),
                                )
                            }
                            ?: throw PersonNotFoundException()
                    } catch (e: BadRequestException) {
                        logger.error(e) { "Ikke gyldig personId" }
                        throw BadRequestException("Ikke gyldig personId")
                    } catch (e: Exception) {
                        logger.error(e) { "Kunne ikke hente person" }
                        throw e
                    }
                }
            }
        }
    }
}

data class HttpProblem(
    val type: URI = URI.create("about:blank"),
    val title: String,
    val status: Int? = 500,
    val detail: String? = null,
    val instance: URI = URI.create("about:blank"),
)

data class PersonNotFoundException(
    override val message: String = "Finner ikke person",
) : Exception(message)

data class IdentBody(
    val ident: String,
)

data class PersonIdBody(
    val personId: Long,
)
