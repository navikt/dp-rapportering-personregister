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
import no.nav.dagpenger.rapportering.personregister.mediator.service.PersonService
import no.nav.dagpenger.rapportering.personregister.mediator.service.SøknadService
import no.nav.dagpenger.rapportering.personregister.mediator.utils.validerIdent
import java.net.URI
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

private val logger = KotlinLogging.logger {}
private val sikkerlogg = KotlinLogging.logger("tjenestekall")

internal fun Application.personApi(
    personService: PersonService,
    søknadService: SøknadService,
) {
    routing {
        authenticate("azureAd") {
            route("/hentPersonId") {
                post {
                    logger.info { "POST /hentPersonId" }

                    val ident = call.receive<IdentBody>().ident
                    validerIdent(ident)

                    personService
                        .hentPersonId(ident = ident)
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

            route("/api/person/søknad/innsendt-tidspunkt") {
                post {
                    logger.info {
                        "POST /api/person/søknad/innsendt-tidspunkt"
                    }

                    val personSøknadInnsendtTidspunktRequest = call.receive<PersonSøknadInnsendtTidspunktRequest>()
                    validerIdent(personSøknadInnsendtTidspunktRequest.ident)

                    søknadService
                        .hentSøknadInnsendtTidspunkt(
                            ident = personSøknadInnsendtTidspunktRequest.ident,
                            søknadId = personSøknadInnsendtTidspunktRequest.søknadId,
                        ).also { innsendtTidspunkt ->
                            call.respond(
                                OK,
                                PersonSøknadInnsendtTidspunktResponse(innsendtTidspunkt),
                            )
                        }
                }
            }
        }
    }
}

private fun validerIdent(ident: String) {
    try {
        ident.validerIdent()
    } catch (e: IllegalArgumentException) {
        val melding = "Validering av ident feilet, se sikker logg for ident: ${e.message}"
        sikkerlogg.error { "Validering av ident \"$ident\" feilet: ${e.message}" }
        logger.error { melding }
        throw BadRequestException(melding)
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

data class PersonSøknadInnsendtTidspunktRequest(
    val ident: String,
    val søknadId: UUID,
)

data class PersonSøknadInnsendtTidspunktResponse(
    val innsendtTidspunkt: LocalDateTime,
)
