package no.nav.dagpenger.rapportering.personregister.mediator

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.statuspages.StatusPagesConfig
import io.ktor.server.response.respond
import no.nav.dagpenger.rapportering.personregister.mediator.api.HttpProblem
import no.nav.dagpenger.rapportering.personregister.mediator.api.PersonNotFoundException

private val logger = KotlinLogging.logger {}

internal fun StatusPagesConfig.statusPagesConfig() {
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
