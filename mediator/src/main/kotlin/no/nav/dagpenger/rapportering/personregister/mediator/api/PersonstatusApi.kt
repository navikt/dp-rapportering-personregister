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
import no.nav.dagpenger.rapportering.personregister.api.models.AnsvarligSystemResponse
import no.nav.dagpenger.rapportering.personregister.api.models.PersonStatusResponse
import no.nav.dagpenger.rapportering.personregister.api.models.StatusResponse
import no.nav.dagpenger.rapportering.personregister.mediator.Configuration.defaultObjectMapper
import no.nav.dagpenger.rapportering.personregister.mediator.PersonMediator
import no.nav.dagpenger.rapportering.personregister.mediator.api.auth.ident
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.SynkroniserPersonMetrikker
import no.nav.dagpenger.rapportering.personregister.mediator.service.PersonService
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.PersonIkkeDagpengerSynkroniseringHendelse
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.PersonSynkroniseringHendelse
import no.nav.dagpenger.rapportering.personregister.modell.overtattBekreftelse
import java.time.LocalDateTime
import java.util.UUID

private val logger = KotlinLogging.logger {}

internal fun Application.personstatusApi(
    personMediator: PersonMediator,
    synkroniserPersonMetrikker: SynkroniserPersonMetrikker,
    personService: PersonService,
) {
    routing {
        authenticate("tokenX") {
            route("/personstatus") {
                post {
                    logger.info { "POST /personstatus" }
                    val ident = call.ident()

                    var dagpengerbruker: Boolean? = null
                    try {
                        val json = defaultObjectMapper.readTree(call.receiveText())
                        dagpengerbruker = json["dagpengerbruker"]?.asBoolean()
                    } catch (e: Exception) {
                        logger.warn(e) { "Kunne ikke lese request ved POST /personstatus. Gammel format?" }
                    }

                    if (dagpengerbruker == false) {
                        personMediator.behandle(
                            PersonIkkeDagpengerSynkroniseringHendelse(
                                ident = ident,
                                dato = LocalDateTime.now(),
                                startDato = LocalDateTime.now(),
                                referanseId = UUID.randomUUID().toString(),
                            ),
                        )
                    } else {
                        personMediator.behandle(
                            PersonSynkroniseringHendelse(
                                ident = ident,
                                dato = LocalDateTime.now(),
                                startDato = LocalDateTime.now(),
                                referanseId = UUID.randomUUID().toString(),
                            ),
                        )
                    }

                    synkroniserPersonMetrikker.personSynkronisert.increment()

                    call.respond(HttpStatusCode.OK)
                }

                get {
                    logger.info { "GET /personstatus" }
                    val ident = call.ident()

                    try {
                        personService
                            .hentPerson(ident)
                            ?.also { person ->
                                call.respond(
                                    HttpStatusCode.OK,
                                    PersonStatusResponse(
                                        ident = person.ident,
                                        status = StatusResponse.valueOf(person.status.name),
                                        overtattBekreftelse = person.overtattBekreftelse,
                                        ansvarligSystem =
                                            person.ansvarligSystem?.let {
                                                AnsvarligSystemResponse.valueOf(
                                                    it.name,
                                                )
                                            },
                                    ),
                                )
                            }
                            ?: call.respond(HttpStatusCode.NotFound, "Finner ikke status for person")
                    } catch (e: Exception) {
                        logger.error(e) { "Kunne ikke hente personstatus" }
                        call.respond(HttpStatusCode.InternalServerError, "Kunne ikke hente personstatus")
                    }
                }
            }
        }
    }
}
