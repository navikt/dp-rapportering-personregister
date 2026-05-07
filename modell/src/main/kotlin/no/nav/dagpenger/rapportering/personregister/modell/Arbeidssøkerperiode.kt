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
) {
    enum class ÅrsakTilUtmelding(
        val dbValue: String,
    ) {
        UTMELDT_I_ARBEIDSSØKERREGISTERET("UTMELDT_I_ARBEIDSSØKERREGISTERET"),
        IKKE_MELDT_SEG_PÅ_21_DAGER("IKKE_MELDT_SEG_PÅ_21_DAGER"),
        UTMELDT_PÅ_MELDEKORT("UTMELDT_PÅ_MELDEKORT"),
        ;

        companion object {
            fun fromDbValue(dbValue: String): ÅrsakTilUtmelding? = entries.find { it.dbValue == dbValue }
        }
    }
}

fun Arbeidssøkerperiode.aktiv(): Boolean = avsluttet == null

val List<Arbeidssøkerperiode>.gjeldende: Arbeidssøkerperiode?
    get() = this.firstOrNull { it.aktiv() }
