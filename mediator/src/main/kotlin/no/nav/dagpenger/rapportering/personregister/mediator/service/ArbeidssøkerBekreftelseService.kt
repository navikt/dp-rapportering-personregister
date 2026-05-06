package no.nav.dagpenger.rapportering.personregister.mediator.service

import no.nav.dagpenger.rapportering.personregister.mediator.connector.ArbeidssøkerBekreftelseConnector
import no.nav.dagpenger.rapportering.personregister.mediator.connector.ArbeidssøkerConnector
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.ArbeidssøkerBekreftelseMelding

class ArbeidssøkerBekreftelseService(
    private val arbeidssøkerConnector: ArbeidssøkerConnector,
    private val arbeidssøkerBekreftelseConnector: ArbeidssøkerBekreftelseConnector,
) {
    suspend fun behandle(melding: ArbeidssøkerBekreftelseMelding) {
        val recordKey = arbeidssøkerConnector.hentRecordKey(melding.ident).key

        arbeidssøkerBekreftelseConnector.sendBekreftelse(recordKey, melding)
    }
}
