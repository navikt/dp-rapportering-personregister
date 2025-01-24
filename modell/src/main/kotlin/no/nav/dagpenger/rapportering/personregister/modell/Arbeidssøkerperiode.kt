package no.nav.dagpenger.rapportering.personregister.modell

import java.time.LocalDateTime
import java.util.UUID

data class ArbeidssøkerperiodeLøsning(
    val ident: String,
    val løsning: Arbeidssøkerperiode?,
    val feil: String?,
)

data class Arbeidssøkerperiode(
    val periodeId: UUID,
    val ident: String,
    val startet: LocalDateTime,
    val avsluttet: LocalDateTime?,
    val overtattBekreftelse: Boolean?,
)

data class OvertaBekreftelseLøsning(
    val ident: String,
    val periodeId: UUID,
    val løsning: String?,
    val feil: String?,
)
