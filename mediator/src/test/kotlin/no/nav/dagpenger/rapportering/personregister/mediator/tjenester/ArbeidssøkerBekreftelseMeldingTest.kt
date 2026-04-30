package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.Test

class ArbeidssøkerBekreftelseMeldingTest {
    @Test
    fun `arbeidssøkerbekreftelse mappes riktig fra JsonMessage`() {
        val ident = "12345678910"
        val periodeId = UUID.fromString("11111111-1111-1111-1111-111111111111")
        val bekreftelseId = UUID.fromString("22222222-2222-2222-2222-222222222222")
        val tidspunkt = LocalDateTime.parse("2025-01-02T10:15:30")
        val gjelderFra = LocalDateTime.parse("2025-01-01T00:00:00")
        val gjelderTil = LocalDateTime.parse("2025-01-14T23:59:59")

        val jsonMessage =
            ArbeidssøkerBekreftelseTestData.jsonMessage(
                ident = ident,
                periodeId = periodeId,
                bekreftelseId = bekreftelseId,
                tidspunkt = tidspunkt,
                gjelderFra = gjelderFra,
                gjelderTil = gjelderTil,
                harJobbetIDennePerioden = true,
                vilFortsetteSomArbeidssøker = true,
            )

        val resultat = jsonMessage.tilArbeidssøkerBekreftelseMelding()

        resultat shouldBe
            ArbeidssøkerBekreftelseMelding(
                ident = ident,
                bekreftelse =
                    Bekreftelse(
                        id = bekreftelseId,
                        periodeId = periodeId,
                        bekreftelsesløsning = Bekreftelsesløsning.DAGPENGER,
                        svar =
                            Svar(
                                sendtInnAv =
                                    SendtInnAv(
                                        tidspunkt = tidspunkt,
                                        utførtAv =
                                            UtførtAv(
                                                type = "Person",
                                                ident = ident,
                                                sikkerhetsnivå = "1",
                                            ),
                                        kilde = "DAGPENGER",
                                        årsak = "VET IKKE",
                                    ),
                                gjelderFra = gjelderFra,
                                gjelderTil = gjelderTil,
                                harJobbetIDennePerioden = true,
                                vilFortsetteSomArbeidssøker = true,
                            ),
                    ),
            )
    }
}
