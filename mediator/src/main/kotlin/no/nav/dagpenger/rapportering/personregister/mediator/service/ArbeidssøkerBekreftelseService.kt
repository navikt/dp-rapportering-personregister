package no.nav.dagpenger.rapportering.personregister.mediator.service

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.connector.ArbeidssøkerBekreftelseConnector
import no.nav.dagpenger.rapportering.personregister.mediator.connector.ArbeidssøkerConnector
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.ArbeidssøkerBekreftelseMelding

private val sikkerlogg = KotlinLogging.logger("tjenestekall")

class ArbeidssøkerBekreftelseService(
    private val arbeidssøkerConnector: ArbeidssøkerConnector,
    private val arbeidssøkerBekreftelseConnector: ArbeidssøkerBekreftelseConnector,
) {
    suspend fun behandle(melding: ArbeidssøkerBekreftelseMelding) {
        try {
            sikkerlogg.info { "Behandle arbeidssøkerbekreftelse: $melding" }
            val recordKey = arbeidssøkerConnector.hentRecordKey(melding.ident).key

            arbeidssøkerBekreftelseConnector.sendBekreftelse(recordKey, melding)
        } catch (e: Exception) {
            sikkerlogg.error(e) { "Feil ved behandling av arbeidssøkerbekreftelse for ident ${melding.ident}" }
            throw e
        }
    }
}
