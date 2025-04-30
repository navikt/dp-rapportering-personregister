package no.nav.dagpenger.rapportering.personregister.mediator.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.api.models.PersonResponse
import no.nav.dagpenger.rapportering.personregister.api.models.StatusResponse
import no.nav.dagpenger.rapportering.personregister.mediator.Configuration
import no.nav.dagpenger.rapportering.personregister.mediator.PersonMediator
import no.nav.dagpenger.rapportering.personregister.mediator.api.auth.ident
import no.nav.dagpenger.rapportering.personregister.mediator.api.auth.jwt
import no.nav.dagpenger.rapportering.personregister.mediator.connector.MeldepliktConnector
import no.nav.dagpenger.rapportering.personregister.mediator.connector.PdlConnector
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.SynkroniserPersonMetrikker
import no.nav.dagpenger.rapportering.personregister.modell.PersonSynkroniseringHendelse
import no.nav.dagpenger.rapportering.personregister.modell.overtattBekreftelse
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

private val logger = KotlinLogging.logger {}

internal fun Application.personstatusApi(
    personRepository: PersonRepository,
    pdlConnector: PdlConnector,
    personMediator: PersonMediator,
    synkroniserPersonMetrikker: SynkroniserPersonMetrikker,
    meldepliktConnector: MeldepliktConnector,
) {
    routing {
        route("/pdl/identer") {
            post {
                val ident = call.receiveText()
                val identer = pdlConnector.hentIdenter(ident, Configuration.pdlApiTokenProvider.invoke())
                call.respond(identer)
            }
        }
        route("/pdl/person") {
            post {
                val ident = call.receiveText()
                val person = pdlConnector.hentPerson(ident, Configuration.pdlApiTokenProvider.invoke())
                call.respond(person)
            }
        }
        authenticate("tokenX") {
            route("/personstatus") {
                post {
                    logger.info { "POST /personstatus" }
                    val ident = call.ident()

                    val rawText = call.receiveText()
                    val dateText = rawText.trim('"')
                    val fraDato = LocalDate.parse(dateText).atStartOfDay()

                    personMediator.behandle(
                        PersonSynkroniseringHendelse(
                            ident = ident,
                            dato = LocalDateTime.now(),
                            startDato = fraDato,
                            referanseId = UUID.randomUUID().toString(),
                        ),
                    )

                    synkroniserPersonMetrikker.personSynkronisert.increment()

                    call.respond(HttpStatusCode.OK)
                }

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
                                    status = StatusResponse.valueOf(it.status.name),
                                    overtattBekreftelse = it.overtattBekreftelse,
                                ),
                            )
                        }
                        ?: call.respond(HttpStatusCode.NotFound, "Finner ikke status for person")
                }
            }

            route("/identer") {
                get {
                    logger.info { "GET /identer" }
                    val ident = call.ident()
                    val jwtToken = call.request.jwt()
                    pdlConnector
                        .hentIdenter(ident, jwtToken)
                        .also {
                            call.respond(HttpStatusCode.OK, it)
                        }
                }
            }
        }
    }
}
