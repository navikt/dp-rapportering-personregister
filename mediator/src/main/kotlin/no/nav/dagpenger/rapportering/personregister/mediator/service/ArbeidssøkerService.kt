package no.nav.dagpenger.rapportering.personregister.mediator.service

import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.connector.ArbeidssøkerConnector
import no.nav.dagpenger.rapportering.personregister.mediator.hendelser.ArbeidssøkerHendelse
import java.util.UUID

class ArbeidssøkerService(
    private val arbeidssøkerConnector: ArbeidssøkerConnector,
) {
    suspend fun overtaArbeidssøkerRapporering(ident: String) {
        TODO()
    }

    suspend fun hentArbeidssøkerHendelse(ident: String): ArbeidssøkerHendelse? {
        val sisteArbeidssøkerperiodeListe = arbeidssøkerConnector.hentSisteArbeidssøkerperiode(ident)
        return if (sisteArbeidssøkerperiodeListe.isEmpty()) {
            logger.error { "Fant ingen arbeidssøkerperiode" }
            sikkerlogg.error { "Fant ingen arbeidssøkerperiode for ident = $ident" }
            null
        } else {
            if (sisteArbeidssøkerperiodeListe.first().avsluttet == null) {
                sisteArbeidssøkerperiodeListe.first().let {
                    ArbeidssøkerHendelse(ident = ident, periodeId = it.periodeId, dato = it.startet.tidspunkt)
                }
            } else {
                null
            }
        }
    }

    suspend fun hentRegistreringsperiodeId(ident: String): UUID =
        hentArbeidssøkerHendelse(ident)?.periodeId
            ?: throw RuntimeException("Fant ingen aktiv arbeidssøkerperiode")

    companion object {
        private val logger = KotlinLogging.logger {}
        val sikkerlogg = KotlinLogging.logger("tjenestekall.HentRapporteringperioder")
    }
}
