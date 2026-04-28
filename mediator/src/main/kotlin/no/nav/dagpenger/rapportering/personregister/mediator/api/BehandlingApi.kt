package no.nav.dagpenger.rapportering.personregister.mediator.api

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import no.nav.dagpenger.rapportering.personregister.mediator.db.BehandlingRepository
import no.nav.dagpenger.rapportering.personregister.mediator.utils.validerIdent

private val logger = KotlinLogging.logger {}

internal fun Application.behandlingApi(behandlingRepository: BehandlingRepository) {
    routing {
        authenticate("azureAd", "tokenX") {
            route("/hentSisteSakId") {
                post {
                    logger.info { "POST /hentSisteSakId" }
                    val request = call.receive<IdentBody>()

                    try {
                        request.ident.validerIdent()
                    } catch (e: IllegalArgumentException) {
                        val melding = "Validering av ident feilet: $e.message"
                        logger.error { melding }
                        throw BadRequestException(melding)
                    }

                    behandlingRepository
                        .hentSisteSakId(request.ident)
                        ?.also { sakId ->
                            call.respond(
                                HttpStatusCode.OK,
                                SakIdBody(sakId),
                            )
                        }
                        ?: call.respond(HttpStatusCode.NoContent, "Kunne ikke finne sakId")
                }
            }
        }
    }
}

data class SakIdBody(
    val sakId: String,
)
