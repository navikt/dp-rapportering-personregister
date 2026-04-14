package no.nav.dagpenger.rapportering.personregister.mediator.service

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.rapportering.personregister.mediator.connector.MeldekortResponse
import no.nav.dagpenger.rapportering.personregister.mediator.connector.MeldekortregisterConnector
import org.junit.jupiter.api.Test
import java.time.LocalDate

class MeldekortStatusServiceTest {
    private val meldekortregisterConnector = mockk<MeldekortregisterConnector>()
    private val service = MeldekortStatusService(meldekortregisterConnector)

    private val ident = "12345678901"
    private val idag = LocalDate.now()

    @Test
    fun `har innsendte og meldekort til utfylling - redirect til meldekortflate`() {
        coEvery { meldekortregisterConnector.hentMeldekort(ident) } returns
            listOf(
                innsendtMeldekort(),
                meldekortTilUtfylling(kanSendesFra = idag, sisteFristForTrekk = idag.plusDays(7)),
            )

        val response = runBlocking { service.hentMeldekortStatus(ident) }

        response.harInnsendteMeldekort shouldBe true
        response.meldekortTilUtfylling.size shouldBe 1
        response.redirectUrl shouldBe "/arbeid/dagpenger/meldekort"
    }

    @Test
    fun `har innsendte men ingen meldekort til utfylling - redirect til meldekortflate`() {
        coEvery { meldekortregisterConnector.hentMeldekort(ident) } returns
            listOf(
                innsendtMeldekort(),
            )

        val response = runBlocking { service.hentMeldekortStatus(ident) }

        response.harInnsendteMeldekort shouldBe true
        response.meldekortTilUtfylling shouldBe emptyList()
        response.redirectUrl shouldBe "/arbeid/dagpenger/meldekort"
    }

    @Test
    fun `ingen innsendte men har meldekort til utfylling - redirect til meldekortflate`() {
        coEvery { meldekortregisterConnector.hentMeldekort(ident) } returns
            listOf(
                meldekortTilUtfylling(kanSendesFra = idag, sisteFristForTrekk = idag.plusDays(7)),
            )

        val response = runBlocking { service.hentMeldekortStatus(ident) }

        response.harInnsendteMeldekort shouldBe false
        response.meldekortTilUtfylling.size shouldBe 1
        response.redirectUrl shouldBe "/arbeid/dagpenger/meldekort"
    }

    @Test
    fun `ingen innsendte og ingen meldekort til utfylling - ingen redirect`() {
        coEvery { meldekortregisterConnector.hentMeldekort(ident) } returns emptyList()

        val response = runBlocking { service.hentMeldekortStatus(ident) }

        response.harInnsendteMeldekort shouldBe false
        response.meldekortTilUtfylling shouldBe emptyList()
        response.redirectUrl shouldBe ""
    }

    @Test
    fun `returnerer tom default response når kall til meldekortregister feiler`() {
        coEvery { meldekortregisterConnector.hentMeldekort(ident) } throws RuntimeException("Meldekortregister utilgjengelig")

        val response = runBlocking { service.hentMeldekortStatus(ident) }

        response.harInnsendteMeldekort shouldBe false
        response.meldekortTilUtfylling shouldBe emptyList()
        response.redirectUrl shouldBe ""
    }

    private fun innsendtMeldekort() =
        MeldekortResponse(
            status = "Innsendt",
            kanSendesFra = idag.minusDays(14),
            sisteFristForTrekk = idag.minusDays(7),
        )

    private fun meldekortTilUtfylling(
        kanSendesFra: LocalDate,
        sisteFristForTrekk: LocalDate,
    ) = MeldekortResponse(
        status = "TilUtfylling",
        kanSendesFra = kanSendesFra,
        sisteFristForTrekk = sisteFristForTrekk,
    )
}
