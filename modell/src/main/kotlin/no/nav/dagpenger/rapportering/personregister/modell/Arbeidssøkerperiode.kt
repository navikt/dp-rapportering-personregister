package no.nav.dagpenger.rapportering.personregister.modell

import java.time.LocalDateTime
import java.util.UUID

data class Arbeidssøkerperiode(
    val periodeId: UUID,
    val ident: String,
    val startet: LocalDateTime,
    var avsluttet: LocalDateTime?,
    var overtattBekreftelse: Boolean?,
    var årsakTilUtmelding: ÅrsakTilUtmelding? = null,
)

fun Arbeidssøkerperiode.aktiv(): Boolean = avsluttet == null

val List<Arbeidssøkerperiode>.gjeldende: Arbeidssøkerperiode?
    get() = this.firstOrNull { it.aktiv() }

enum class ÅrsakTilUtmelding {
    UTMELDT_I_ASR,
    MELDEPLIKT_BRUTT,
    UTMELDT_PÅ_MELDEKORT,
}
