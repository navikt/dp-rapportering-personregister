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
import no.nav.dagpenger.rapportering.personregister.mediator.ArbeidssøkerMediator
import no.nav.dagpenger.rapportering.personregister.mediator.PersonMediator
import no.nav.dagpenger.rapportering.personregister.mediator.api.auth.ident
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.SynkroniserPersonMetrikker
import no.nav.dagpenger.rapportering.personregister.modell.PersonSynkroniseringHendelse
import java.time.LocalDate
import java.util.UUID

private val logger = KotlinLogging.logger {}

internal fun Application.personstatusApi(
    personRepository: PersonRepository,
    arbeidssøkerMediator: ArbeidssøkerMediator,
    personMediator: PersonMediator,
    synkroniserPersonMetrikker: SynkroniserPersonMetrikker,
) {
    routing {
        authenticate("tokenX") {
            route("/personstatus") {
                post {
                    logger.info { "POST /personstatus" }
                    val ident = call.ident()

                    val fraDato = LocalDate.parse(call.receiveText()).atStartOfDay()

                    personMediator.behandle(
                        PersonSynkroniseringHendelse(
                            ident = ident,
                            dato = fraDato,
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
                                ),
                            )
                        }
                        ?: call.respond(HttpStatusCode.NotFound, "Finner ikke status for person")
                }
            }
            route("/test") {
                // TODO: Fjerne når arb.søk-flyt er på plass
                get {
                    logger.info { "GET /test" }
                    val ident = call.ident()
                    arbeidssøkerMediator.behandle(ident)
                    call.respond(HttpStatusCode.OK, "Hello, world!")
                }
            }
        }
    }
}
