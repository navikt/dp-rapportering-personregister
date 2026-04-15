package no.nav.dagpenger.rapportering.personregister.mediator.service

import no.nav.dagpenger.rapportering.personregister.mediator.connector.ArenaMeldekortResponse
import no.nav.dagpenger.rapportering.personregister.mediator.connector.MeldekortResponse
import no.nav.dagpenger.rapportering.personregister.mediator.connector.MeldekortregisterConnector
import no.nav.dagpenger.rapportering.personregister.mediator.connector.MeldepliktConnector

class MeldekortStatusService(
    private val meldekortregisterConnector: MeldekortregisterConnector,
    private val meldepliktConnector: MeldepliktConnector,
) {
    suspend fun hentMeldekortStatus(ident: String): MeldekortStatus =
        hentFraMeldekortregister(ident)
            ?: hentFraArena(ident)
            ?: MeldekortStatus(
                harInnsendteMeldekort = false,
                meldekortTilUtfylling = emptyList(),
                redirectUrl = RedirectUrl.INGEN.url,
            )

    private suspend fun hentFraMeldekortregister(ident: String): MeldekortStatus? {
        val meldekort = meldekortregisterConnector.hentMeldekort(ident)
        if (meldekort.isEmpty()) return null
        return MeldekortStatus(
            harInnsendteMeldekort = meldekort.harInnsendte(),
            meldekortTilUtfylling = meldekort.tilUtfylling(),
            redirectUrl = RedirectUrl.DP_MELDEKORT.url,
        )
    }

    private suspend fun hentFraArena(ident: String): MeldekortStatus? {
        val meldekort = meldepliktConnector.hentMeldekortFraArena(ident)
        if (meldekort.isEmpty()) return null
        val harInnsendte = meldekort.harInnsendte()
        val tilUtfylling = meldekort.tilUtfylling()
        return MeldekortStatus(
            harInnsendteMeldekort = harInnsendte,
            meldekortTilUtfylling = tilUtfylling.map {
                MeldekortResponse(
                    status = it.status,
                    kanSendesFra = it.kanSendesFra,
                    sisteFristForTrekk = it.kanSendesFra.plusDays(9),
                )
            },
            redirectUrl = if (harInnsendte || tilUtfylling.isNotEmpty()) RedirectUrl.DP_MELDEKORT.url else RedirectUrl.FELLES_MELDEKORT.url,
        )
    }
}

@JvmName("meldekortHarInnsendte")
private fun List<MeldekortResponse>.harInnsendte() = any { it.status == "Innsendt" }

@JvmName("arenaHarInnsendte")
private fun List<ArenaMeldekortResponse>.harInnsendte() = any { it.status == "Innsendt" }

@JvmName("meldekortTilUtfylling")
private fun List<MeldekortResponse>.tilUtfylling() = filter { it.status == "TilUtfylling" }

@JvmName("arenaTilUtfylling")
private fun List<ArenaMeldekortResponse>.tilUtfylling() = filter { it.status == "TilUtfylling" }

data class MeldekortStatus(
    val harInnsendteMeldekort: Boolean,
    val meldekortTilUtfylling: List<MeldekortResponse>,
    val redirectUrl: String,
)

private enum class RedirectUrl(
    val url: String,
) {
    DP_MELDEKORT("/arbeid/dagpenger/meldekort"),
    FELLES_MELDEKORT("/felles-meldekort"),
    INGEN(""),
}
