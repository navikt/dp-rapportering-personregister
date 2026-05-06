package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import java.time.LocalDateTime
import java.util.UUID

internal object ArbeidssøkerBekreftelseTestData {
    const val ident = "12345678910"

    val periodeId: UUID = UUID.fromString("11111111-1111-1111-1111-111111111111")
    val bekreftelseId: UUID = UUID.fromString("22222222-2222-2222-2222-222222222222")

    val tidspunkt: LocalDateTime = LocalDateTime.parse("2025-01-02T10:15:30")
    val gjelderFra: LocalDateTime = LocalDateTime.parse("2025-01-01T00:00:00")
    val gjelderTil: LocalDateTime = LocalDateTime.parse("2025-01-14T23:59:59")

    fun event(
        ident: String = this.ident,
        periodeId: UUID = this.periodeId,
        bekreftelseId: UUID = this.bekreftelseId,
        tidspunkt: LocalDateTime = this.tidspunkt,
        gjelderFra: LocalDateTime = this.gjelderFra,
        gjelderTil: LocalDateTime = this.gjelderTil,
        harJobbetIDennePerioden: Boolean = true,
        vilFortsetteSomArbeidssøker: Boolean = true,
    ): String =
        // language=json
        """
        {
          "@event_name": "arbeidssøkerbekreftelse",
          "ident": "$ident",
          "bekreftelse": {
            "periodeId": "$periodeId",
            "bekreftelsesløsning": "DAGPENGER",
            "id": "$bekreftelseId",
            "svar": {
              "sendtInnAv": {
                "tidspunkt": "$tidspunkt",
                "utførtAv": {
                  "type": "Person",
                  "ident": "$ident",
                  "sikkerhetsnivå": "1"
                },
                "kilde": "DAGPENGER",
                "årsak": "VET IKKE"
              },
              "gjelderFra": "$gjelderFra",
              "gjelderTil": "$gjelderTil",
              "harJobbetIDennePerioden": $harJobbetIDennePerioden,
              "vilFortsetteSomArbeidssøker": $vilFortsetteSomArbeidssøker
            }
          }
        }
        """.trimIndent()

    fun jsonMessage(
        ident: String,
        periodeId: UUID,
        bekreftelseId: UUID,
        tidspunkt: LocalDateTime,
        gjelderFra: LocalDateTime,
        gjelderTil: LocalDateTime,
        harJobbetIDennePerioden: Boolean,
        vilFortsetteSomArbeidssøker: Boolean,
    ): JsonMessage {
        val messageString =
            event(
                ident = ident,
                periodeId = periodeId,
                bekreftelseId = bekreftelseId,
                tidspunkt = tidspunkt,
                gjelderFra = gjelderFra,
                gjelderTil = gjelderTil,
                harJobbetIDennePerioden = harJobbetIDennePerioden,
                vilFortsetteSomArbeidssøker = vilFortsetteSomArbeidssøker,
            )

        return JsonMessage(messageString, MessageProblems(messageString)).also {
            it.requireKey("ident")
            it.requireKey("bekreftelse")
        }
    }
}
