package no.nav.dagpenger.rapportering.personregister.mediator.api

import io.ktor.server.application.Application
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

internal fun Application.personstatusApi() {
    logger.info { "Registering PersonstatusApi" }
}
