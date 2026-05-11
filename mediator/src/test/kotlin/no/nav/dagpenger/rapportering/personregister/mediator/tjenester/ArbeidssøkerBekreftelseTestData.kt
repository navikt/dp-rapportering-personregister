package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import java.time.Instant
import java.util.UUID

internal object ArbeidssøkerBekreftelseTestData {
    const val ident = "12345678910"

    val periodeId: UUID = UUID.randomUUID()
    val bekreftelseId: UUID = UUID.randomUUID()

    val tidspunkt: Instant = Instant.parse("2025-01-02T10:15:30Z")
    val gjelderFra: Instant = Instant.parse("2025-01-01T00:00:00Z")
    val gjelderTil: Instant = Instant.parse("2025-01-14T23:59:59Z")

    fun event(
        ident: String = this.ident,
        periodeId: UUID = this.periodeId,
        bekreftelseId: UUID = this.bekreftelseId,
        tidspunkt: Instant = this.tidspunkt,
        gjelderFra: Instant = this.gjelderFra,
        gjelderTil: Instant = this.gjelderTil,
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
        tidspunkt: Instant,
        gjelderFra: Instant,
        gjelderTil: Instant,
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
