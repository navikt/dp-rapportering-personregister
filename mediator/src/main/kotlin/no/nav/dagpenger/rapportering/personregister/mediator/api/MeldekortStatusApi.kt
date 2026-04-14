package no.nav.dagpenger.rapportering.personregister.mediator.api

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import no.nav.dagpenger.rapportering.personregister.api.models.MeldekortStatusResponse
import no.nav.dagpenger.rapportering.personregister.api.models.MeldekortTilUtfyllingResponse
import no.nav.dagpenger.rapportering.personregister.mediator.api.auth.ident
import no.nav.dagpenger.rapportering.personregister.mediator.connector.MeldekortResponse
import no.nav.dagpenger.rapportering.personregister.mediator.service.MeldekortStatus
import no.nav.dagpenger.rapportering.personregister.mediator.service.MeldekortStatusService
import java.time.LocalDate
import java.time.ZoneOffset

private val logger = KotlinLogging.logger {}

internal fun Application.meldekortStatusApi(meldekortStatusService: MeldekortStatusService) {
    routing {
        authenticate("tokenX") {
            get("/meldekort/status") {
                logger.info { "GET /meldekort/status" }
                val status = meldekortStatusService.hentStatus(call.ident())
                call.respond(HttpStatusCode.OK, status.tilResponse())
            }
        }
    }
}

private fun MeldekortStatus.tilResponse() =
    MeldekortStatusResponse(
        harInnsendteMeldekort = harInnsendteMeldekort,
        redirectUrl = redirectUrl,
        meldekortTilUtfylling = meldekortTilUtfylling.map { it.tilMeldekortTilUtfylling() },
    )

private fun MeldekortResponse.tilMeldekortTilUtfylling() =
    MeldekortTilUtfyllingResponse(
        kanSendesFra = kanSendesFra.tilOffsetDateTime(),
        fristForInnsending = sisteFristForTrekk.tilOffsetDateTime(),
    )

private fun LocalDate.tilOffsetDateTime() = atStartOfDay().atOffset(ZoneOffset.UTC)
