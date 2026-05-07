package no.nav.dagpenger.rapportering.personregister.mediator.service

import io.kotest.assertions.throwables.shouldThrow
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.rapportering.personregister.mediator.connector.ArbeidssøkerBekreftelseConnector
import no.nav.dagpenger.rapportering.personregister.mediator.connector.ArbeidssøkerConnector
import no.nav.dagpenger.rapportering.personregister.mediator.connector.RecordKeyResponse
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.ArbeidssøkerBekreftelseMelding
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.Bekreftelse
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.Bekreftelsesløsning
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.Bruker
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.SendtInnAv
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.Svar
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class ArbeidssøkerBekreftelseServiceTest {
    private val arbeidssøkerConnector = mockk<ArbeidssøkerConnector>()
    private val arbeidssøkerBekreftelseConnector = mockk<ArbeidssøkerBekreftelseConnector>()
    private val service = ArbeidssøkerBekreftelseService(arbeidssøkerConnector, arbeidssøkerBekreftelseConnector)

    private val ident = "12345678910"
    private val recordKey = 42L

    @Test
    fun `henter recordKey og sender bekreftelse`() =
        runBlocking {
            val melding = bekreftelseMelding()

            coEvery { arbeidssøkerConnector.hentRecordKey(ident) } returns RecordKeyResponse(recordKey)
            coEvery { arbeidssøkerBekreftelseConnector.sendBekreftelse(any(), any()) } returns melding.bekreftelse.id

            service.behandle(melding)

            coVerify { arbeidssøkerConnector.hentRecordKey(ident) }
            coVerify { arbeidssøkerBekreftelseConnector.sendBekreftelse(recordKey, melding) }
        }

    @Test
    fun `kaster exception når hentRecordKey feiler`() =
        runBlocking {
            val melding = bekreftelseMelding()

            coEvery { arbeidssøkerConnector.hentRecordKey(ident) } throws RuntimeException("Feil")

            shouldThrow<RuntimeException> { service.behandle(melding) }

            coVerify(exactly = 0) { arbeidssøkerBekreftelseConnector.sendBekreftelse(any(), any()) }
        }

    @Test
    fun `kaster exception når sendBekreftelse feiler`(): Unit =
        runBlocking {
            val melding = bekreftelseMelding()

            coEvery { arbeidssøkerConnector.hentRecordKey(ident) } returns RecordKeyResponse(recordKey)
            coEvery { arbeidssøkerBekreftelseConnector.sendBekreftelse(any(), any()) } throws RuntimeException("Kafka feil")

            shouldThrow<RuntimeException> { service.behandle(melding) }
        }

    private fun bekreftelseMelding() =
        ArbeidssøkerBekreftelseMelding(
            ident = ident,
            bekreftelse =
                Bekreftelse(
                    id = UUID.randomUUID(),
                    periodeId = UUID.randomUUID(),
                    bekreftelsesløsning = Bekreftelsesløsning.DAGPENGER,
                    svar =
                        Svar(
                            sendtInnAv =
                                SendtInnAv(
                                    tidspunkt = LocalDateTime.now(),
                                    utførtAv = Bruker(type = "SLUTTBRUKER", ident = ident, sikkerhetsnivå = "idporten:Level4"),
                                    kilde = "DAGPENGER",
                                    årsak = "Bruker sendte inn dagpengermeldekort",
                                ),
                            gjelderFra = LocalDateTime.of(2025, 1, 1, 0, 0),
                            gjelderTil = LocalDateTime.of(2025, 1, 14, 23, 59, 59),
                            harJobbetIDennePerioden = true,
                            vilFortsetteSomArbeidssøker = true,
                        ),
                ),
        )
}
