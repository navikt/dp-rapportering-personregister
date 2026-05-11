package no.nav.dagpenger.rapportering.personregister.mediator.connector

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import no.nav.dagpenger.rapportering.personregister.mediator.ZONE_ID
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.ArbeidssøkerBekreftelseMelding
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.Bekreftelse
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.Bekreftelsesløsning
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.Bruker
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.SendtInnAv
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.Svar
import no.nav.paw.bekreftelse.melding.v1.vo.Bekreftelsesloesning
import no.nav.paw.bekreftelse.melding.v1.vo.BrukerType
import no.nav.paw.bekreftelse.melding.v1.vo.Metadata
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID
import no.nav.paw.bekreftelse.melding.v1.Bekreftelse as ASRBekreftelse
import no.nav.paw.bekreftelse.melding.v1.vo.Bruker as ASRBruker
import no.nav.paw.bekreftelse.melding.v1.vo.Svar as ASRSvar

fun ArbeidssøkerBekreftelseMelding.tilBekreftelse(): ASRBekreftelse {
    val svar = bekreftelse.svar
    val bruker = svar.sendtInnAv.utførtAv

    return ASRBekreftelse(
        bekreftelse.periodeId,
        Bekreftelsesloesning.DAGPENGER,
        bekreftelse.id,
        ASRSvar(
            Metadata(
                Instant.now().atZone(ZONE_ID).toInstant(),
                ASRBruker(
                    BrukerType.valueOf(bruker.type),
                    bruker.ident,
                    bruker.sikkerhetsnivå,
                ),
                Bekreftelsesloesning.DAGPENGER.name,
                svar.sendtInnAv.årsak,
            ),
            svar.gjelderFra.atZone(ZONE_ID).toInstant(),
            svar.gjelderTil
                .plusDays(1)
                .atZone(ZONE_ID)
                .toInstant(),
            svar.harJobbetIDennePerioden,
            svar.vilFortsetteSomArbeidssøker,
        ),
    )
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
            tidspunkt = sendtInnAvNode["tidspunkt"].asText().toLocalDateTime(),
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
            gjelderFra = svarNode["gjelderFra"].asText().toLocalDateTime(),
            gjelderTil = svarNode["gjelderTil"].asText().toLocalDateTime(),
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

private fun String.toLocalDateTime(): LocalDateTime = LocalDateTime.ofInstant(Instant.parse(this), ZONE_ID)
