package no.nav.dagpenger.rapportering.personregister.mediator.api

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.HttpStatusCode.Companion.OK
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
import no.nav.dagpenger.rapportering.personregister.api.models.SoknadResponse
import no.nav.dagpenger.rapportering.personregister.mediator.service.PersonService
import no.nav.dagpenger.rapportering.personregister.mediator.service.SøknadService
import no.nav.dagpenger.rapportering.personregister.mediator.utils.validerIdent
import java.net.URI
import java.time.ZoneOffset

private val logger = KotlinLogging.logger {}

internal fun Application.personApi(
    personService: PersonService,
    søknadService: SøknadService,
) {
    routing {
        authenticate("azureAd") {
            route("/hentPersonId") {
                post {
                    logger.info { "POST /hentPersonId" }
                    val request = call.receive<IdentBody>()

                    try {
                        request.ident.validerIdent()
                    } catch (e: IllegalArgumentException) {
                        val melding = "Validering av ident feilet: $e.message"
                        logger.error { melding }
                        throw BadRequestException(melding)
                    }

                    personService
                        .hentPersonId(request.ident)
                        ?.also { personId ->
                            call.respond(
                                OK,
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
                                OK,
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
                                    OK,
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

            route("/api/person/{personId}/søknader") {
                get {
                    val personId =
                        call.parameters["personId"]?.toLongOrNull()
                            ?: throw BadRequestException("Mangler eller ugyldig personId")

                    søknadService
                        .hentSøknader(personId)
                        .also { søknader ->
                            call.respond(
                                OK,
                                søknader.map { søknad ->
                                    SoknadResponse(
                                        søknadId = søknad.søknadId,
                                        innsendtTidspunkt = søknad.innsendtTidspunkt.toString(),
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
