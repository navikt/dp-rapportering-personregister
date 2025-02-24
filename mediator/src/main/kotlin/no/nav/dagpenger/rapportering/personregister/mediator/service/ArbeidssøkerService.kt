package no.nav.dagpenger.rapportering.personregister.mediator.service

import no.nav.dagpenger.rapportering.personregister.mediator.connector.ArbeidssøkerConnector
import no.nav.dagpenger.rapportering.personregister.modell.Arbeidssøkerperiode

class ArbeidssøkerService(
    private val arbeidssøkerConnector: ArbeidssøkerConnector,
) {
    suspend fun hentSisteArbeidssøkerperiode(ident: String): Arbeidssøkerperiode? =
        arbeidssøkerConnector.hentSisteArbeidssøkerperiode(ident).firstOrNull()?.let {
            Arbeidssøkerperiode(
                periodeId = it.periodeId,
                startet = it.startet.tidspunkt,
                avsluttet = it.avsluttet?.tidspunkt,
                ident = ident,
                overtattBekreftelse = null,
            )
        }
}
