package no.nav.dagpenger.rapportering.personregister.mediator.service

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.connector.MeldekortResponse
import no.nav.dagpenger.rapportering.personregister.mediator.connector.MeldekortregisterConnector

private val logger = KotlinLogging.logger {}

class MeldekortStatusService(
    private val meldekortregisterConnector: MeldekortregisterConnector,
) {
    suspend fun hentMeldekortStatus(ident: String): MeldekortStatus =
        hentFraMeldekortregister(ident)
            ?: hentFraArena(ident)
            ?: MeldekortStatus(
                harInnsendteMeldekort = false,
                meldekortTilUtfylling = emptyList(),
                redirectUrl = RedirectUrl.INGEN.url,
            )

    private suspend fun hentFraMeldekortregister(ident: String): MeldekortStatus? =
        try {
            val meldekort = meldekortregisterConnector.hentMeldekort(ident)
            val harInnsendte = meldekort.harInnsendte()
            val tilUtfylling = meldekort.tilUtfylling()

            if (meldekort.isEmpty()) {
                null
            } else {
                MeldekortStatus(
                    harInnsendteMeldekort = harInnsendte,
                    meldekortTilUtfylling = tilUtfylling,
                    redirectUrl = RedirectUrl.DP_MELDEKORT.url,
                )
            }
        } catch (e: Exception) {
            logger.warn(e) { "Meldekortregister feilet for ident, faller tilbake på Arena" }
            null
        }

    private suspend fun hentFraArena(ident: String): MeldekortStatus? {
        // TODO: Implementeres senere
        return null
    }
}

private fun List<MeldekortResponse>.harInnsendte() = any { it.status == "Innsendt" }

private fun List<MeldekortResponse>.tilUtfylling() = filter { it.status == "TilUtfylling" }

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
    ;

    companion object {
        fun fra(
            harTilUtfylling: Boolean,
            harInnsendte: Boolean,
        ): RedirectUrl =
            when {
                harTilUtfylling -> DP_MELDEKORT
                harInnsendte -> FELLES_MELDEKORT
                else -> INGEN
            }
    }
}
