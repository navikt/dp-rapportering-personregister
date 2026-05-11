package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import io.kotest.matchers.shouldBe
import no.nav.dagpenger.rapportering.personregister.mediator.ZONE_ID
import no.nav.dagpenger.rapportering.personregister.mediator.connector.tilArbeidssøkerBekreftelseMelding
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.Test

class ArbeidssøkerBekreftelseMeldingTest {
    @Test
    fun `arbeidssøkerbekreftelse mappes riktig fra JsonMessage`() {
        val ident = "12345678910"
        val periodeId = UUID.randomUUID()
        val bekreftelseId = UUID.randomUUID()
        val tidspunkt = Instant.parse("2025-01-02T10:15:30Z")
        val gjelderFra = Instant.parse("2025-01-01T00:00:00Z")
        val gjelderTil = Instant.parse("2025-01-14T23:59:59Z")

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
                                        tidspunkt = LocalDateTime.ofInstant(tidspunkt, ZONE_ID),
                                        utførtAv =
                                            Bruker(
                                                type = "Person",
                                                ident = ident,
                                                sikkerhetsnivå = "1",
                                            ),
                                        kilde = "DAGPENGER",
                                        årsak = "VET IKKE",
                                    ),
                                gjelderFra = LocalDateTime.ofInstant(gjelderFra, ZONE_ID),
                                gjelderTil = LocalDateTime.ofInstant(gjelderTil, ZONE_ID),
                                harJobbetIDennePerioden = true,
                                vilFortsetteSomArbeidssøker = true,
                            ),
                    ),
            )
    }
}
