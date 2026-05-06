package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
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

fun JsonMessage.tilArbeidssøkerBekreftelseMelding(): ArbeidssøkerBekreftelseMelding {
    val ident = this["ident"].asText()
    val bekreftelseNode = this["bekreftelse"]
    val id = UUID.fromString(bekreftelseNode["id"].asText())
    val periodeId = UUID.fromString(bekreftelseNode["periodeId"].asText())
    val bekreftelsesløsning = Bekreftelsesløsning.valueOf(bekreftelseNode["bekreftelsesløsning"].asText())
    val svarNode = bekreftelseNode["svar"]
    val sendtInnAvNode = svarNode["sendtInnAv"]
    val sendtInnAv =
        SendtInnAv(
            tidspunkt = LocalDateTime.parse(sendtInnAvNode["tidspunkt"].asText()),
            utførtAv =
                Bruker(
                    type = sendtInnAvNode["utførtAv"]["type"].asText(),
                    ident = sendtInnAvNode["utførtAv"]["ident"].asText(),
                    sikkerhetsnivå = sendtInnAvNode["utførtAv"]["sikkerhetsnivå"].asText(),
                ),
            kilde = sendtInnAvNode["kilde"].asText(),
            årsak = sendtInnAvNode["årsak"].asText(),
        )
    val svar =
        Svar(
            sendtInnAv = sendtInnAv,
            gjelderFra = LocalDateTime.parse(svarNode["gjelderFra"].asText()),
            gjelderTil = LocalDateTime.parse(svarNode["gjelderTil"].asText()),
            harJobbetIDennePerioden = svarNode["harJobbetIDennePerioden"].asBoolean(),
            vilFortsetteSomArbeidssøker = svarNode["vilFortsetteSomArbeidssøker"].asBoolean(),
        )
    return ArbeidssøkerBekreftelseMelding(
        ident = ident,
        bekreftelse =
            Bekreftelse(
                id = id,
                periodeId = periodeId,
                bekreftelsesløsning = bekreftelsesløsning,
                svar = svar,
            ),
    )
}
