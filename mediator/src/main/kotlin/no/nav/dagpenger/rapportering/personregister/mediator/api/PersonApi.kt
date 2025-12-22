package no.nav.dagpenger.rapportering.personregister.mediator.api

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import no.nav.dagpenger.rapportering.personregister.api.models.ArbeidssokerperiodeResponse
import no.nav.dagpenger.rapportering.personregister.mediator.service.PersonService
import java.net.URI
import java.time.ZoneOffset

private val logger = KotlinLogging.logger {}

internal fun Application.personApi(personService: PersonService) {
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
                }
            }

            route("/arbeidssokerperioder/{personId}") {
                get {
                    val personId =
                        call.parameters["personId"]?.toLongOrNull()
                            ?: throw BadRequestException("Mangler eller ugyldig personId")

                    personService
                        .hentArbeidssokerperioder(personId)
                        .also { perioder ->
                            call
                                .respond(
                                    HttpStatusCode.OK,
                                    perioder.map { periode ->
                                        ArbeidssokerperiodeResponse(
                                            periodeId = periode.periodeId.toString(),
                                            ident = periode.ident,
                                            startDato = periode.startet.atOffset(ZoneOffset.UTC),
                                            sluttDato = periode.avsluttet?.atOffset(ZoneOffset.UTC),
                                            status =
                                                if (periode.avsluttet ==
                                                    null
                                                ) {
                                                    ArbeidssokerperiodeResponse.Status.Startet
                                                } else {
                                                    ArbeidssokerperiodeResponse.Status.Avsluttet
                                                },
                                        )
                                    },
                                )
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
