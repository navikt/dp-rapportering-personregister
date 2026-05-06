package no.nav.dagpenger.rapportering.personregister.mediator.connector

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.ArbeidssøkerBekreftelseMelding
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.Bekreftelse
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.Bekreftelsesløsning
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.Bruker
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.SendtInnAv
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.Svar
import no.nav.paw.bekreftelse.melding.v1.vo.Bekreftelsesloesning
import no.nav.paw.bekreftelse.melding.v1.vo.BrukerType
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class BekreftelseMapperTest {
    @Test
    fun `mapper ArbeidssøkerBekreftelseMelding til Bekreftelse`() {
        val periodeId = UUID.randomUUID()
        val bekreftelseId = UUID.randomUUID()
        val gjelderFra = LocalDateTime.of(2025, 1, 1, 0, 0, 0)
        val gjelderTil = LocalDateTime.of(2025, 1, 14, 23, 59, 59)

        val melding = bekreftelseMelding(periodeId, bekreftelseId, gjelderFra, gjelderTil)

        BekreftelseMapper.tilBekreftelse(melding).apply {
            this.periodeId shouldBe periodeId
            bekreftelsesloesning shouldBe Bekreftelsesloesning.DAGPENGER
            svar shouldNotBe null
            svar.harJobbetIDennePerioden shouldBe true
            svar.vilFortsetteSomArbeidssoeker shouldBe true
        }
    }

    @Test
    fun `gjelderTil utvides med en dag`() {
        val gjelderTil = LocalDateTime.of(2025, 1, 14, 23, 59, 59)
        val melding = bekreftelseMelding(gjelderTil = gjelderTil)

        BekreftelseMapper.tilBekreftelse(melding).apply {
            svar.gjelderTil
                .atZone(java.time.ZoneId.of("Europe/Oslo"))
                .toLocalDateTime() shouldBe gjelderTil.plusDays(1)
        }
    }

    @Test
    fun `bruker mappes korrekt`() {
        val melding = bekreftelseMelding()

        val resultat = BekreftelseMapper.tilBekreftelse(melding)

        resultat.svar.sendtInnAv.utfoertAv.type shouldBe BrukerType.SLUTTBRUKER
        resultat.svar.sendtInnAv.utfoertAv.id shouldBe "12345678910"
    }

    @Test
    fun `ugyldig brukertype kaster exception`() {
        val melding = bekreftelseMelding(brukerType = "UGYLDIG")

        shouldThrow<IllegalArgumentException> {
            BekreftelseMapper.tilBekreftelse(melding)
        }
    }

    private fun bekreftelseMelding(
        periodeId: UUID = UUID.randomUUID(),
        bekreftelseId: UUID = UUID.randomUUID(),
        gjelderFra: LocalDateTime = LocalDateTime.of(2025, 1, 1, 0, 0, 0),
        gjelderTil: LocalDateTime = LocalDateTime.of(2025, 1, 14, 23, 59, 59),
        brukerType: String = "SLUTTBRUKER",
    ) = ArbeidssøkerBekreftelseMelding(
        ident = "12345678910",
        bekreftelse =
            Bekreftelse(
                id = bekreftelseId,
                periodeId = periodeId,
                bekreftelsesløsning = Bekreftelsesløsning.DAGPENGER,
                svar =
                    Svar(
                        sendtInnAv =
                            SendtInnAv(
                                tidspunkt = LocalDateTime.now(),
                                utførtAv =
                                    Bruker(
                                        type = brukerType,
                                        ident = "12345678910",
                                        sikkerhetsnivå = "idporten:Level4",
                                    ),
                                kilde = "DAGPENGER",
                                årsak = "Bruker sendte inn dagpengermeldekort",
                            ),
                        gjelderFra = gjelderFra,
                        gjelderTil = gjelderTil,
                        harJobbetIDennePerioden = true,
                        vilFortsetteSomArbeidssøker = true,
                    ),
            ),
    )
}
