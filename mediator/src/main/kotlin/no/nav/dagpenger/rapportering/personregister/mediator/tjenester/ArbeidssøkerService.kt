package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import no.nav.dagpenger.rapportering.personregister.mediator.connector.ArbeidssøkerConnector

class ArbeidssøkerService(
    private val arbeidssøkerConnector: ArbeidssøkerConnector,
) {
    suspend fun overtaArbeidssøkerRapporering(ident: String) {
        TODO()
    }

    private suspend fun hentRegistreringsperiodeId(ident: String): String = arbeidssøkerConnector.hentSisteArbeidssøkerperiode(ident)
}
