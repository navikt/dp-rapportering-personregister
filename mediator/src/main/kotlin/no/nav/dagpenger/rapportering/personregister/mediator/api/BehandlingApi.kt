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

private val logger = KotlinLogging.logger {}

internal fun Application.behandlingApi(behandlingRepository: BehandlingRepository) {
    routing {
        authenticate("azureAd", "tokenX") {
            route("/hentSisteSakId") {
                post {
                    logger.info { "POST /hentSisteSakId" }
                    val request = call.receive<IdentBody>()

                    if (!request.ident.matches(Regex("[0-9]{11}"))) {
                        logger.error { "Person-ident må ha 11 sifre" }
                        throw BadRequestException("Person-ident må ha 11 sifre")
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
