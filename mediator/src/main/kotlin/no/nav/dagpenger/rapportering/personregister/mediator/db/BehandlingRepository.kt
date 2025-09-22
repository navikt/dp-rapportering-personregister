package no.nav.dagpenger.rapportering.personregister.mediator.db

interface BehandlingRepository {
    fun lagreData(
        behandlingId: String,
        søknadId: String,
        ident: String,
        sakId: String,
    )

    fun hentSisteSakId(ident: String): String?
}
