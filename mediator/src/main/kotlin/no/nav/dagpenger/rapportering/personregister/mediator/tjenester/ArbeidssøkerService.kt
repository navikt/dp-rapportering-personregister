package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.connector.ArbeidssøkerConnector
import java.util.UUID

class ArbeidssøkerService(
    private val arbeidssøkerConnector: ArbeidssøkerConnector,
) {
    suspend fun overtaArbeidssøkerRapporering(ident: String) {
        TODO()
    }

    suspend fun hentRegistreringsperiodeId(ident: String): UUID {
        val sisteArbeidssøkerperiodeListe = arbeidssøkerConnector.hentSisteArbeidssøkerperiode(ident)
        if (sisteArbeidssøkerperiodeListe.isEmpty()) {
            logger.error { "Fant ingen arbeidssøkerperiode" }
            sikkerlogg.error { "Fant ingen arbeidssøkerperiode for ident = $ident" }
            throw IllegalStateException("Fant ingen arbeidssøkerperiode")
        } else {
            return sisteArbeidssøkerperiodeListe.first().periodeId
        }
    }

    companion object {
        private val logger = KotlinLogging.logger {}
        val sikkerlogg = KotlinLogging.logger("tjenestekall.HentRapporteringperioder")
    }
}
