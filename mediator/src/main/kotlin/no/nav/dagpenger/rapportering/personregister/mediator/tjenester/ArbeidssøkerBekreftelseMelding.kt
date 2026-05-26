package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import java.time.LocalDateTime
import java.util.UUID

data class ArbeidssøkerBekreftelseMelding(
    val ident: String,
    val bekreftelse: Bekreftelse,
)

data class Bekreftelse(
    val id: UUID,
    val periodeId: UUID,
    val bekreftelsesløsning: Bekreftelsesløsning,
    val svar: Svar,
)

data class Svar(
    val sendtInnAv: SendtInnAv,
    val gjelderFra: LocalDateTime,
    val gjelderTil: LocalDateTime,
    val harJobbetIDennePerioden: Boolean,
    val vilFortsetteSomArbeidssøker: Boolean,
)

data class SendtInnAv(
    val tidspunkt: LocalDateTime,
    val utførtAv: Bruker,
    val kilde: String,
    val årsak: String,
)

data class Bruker(
    val type: String,
    val ident: String,
    val sikkerhetsnivå: String,
)

enum class Bekreftelsesløsning {
    DAGPENGER,
}
