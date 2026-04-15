package no.nav.dagpenger.rapportering.personregister.mediator.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.rapportering.personregister.mediator.connector.ArenaMeldekortResponse
import no.nav.dagpenger.rapportering.personregister.mediator.connector.MeldekortResponse
import no.nav.dagpenger.rapportering.personregister.mediator.connector.MeldekortregisterConnector
import no.nav.dagpenger.rapportering.personregister.mediator.connector.MeldepliktConnector
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate

class MeldekortStatusServiceTest {
    private val meldekortregisterConnector = mockk<MeldekortregisterConnector>()
    private val meldepliktConnector = mockk<MeldepliktConnector>()
    private val service = MeldekortStatusService(meldekortregisterConnector, meldepliktConnector)

    private val ident = "12345678901"
    private val idag = LocalDate.now()

    @Nested
    inner class HentFraMeldekortregister {
        @Test
        fun `innsendt og til utfylling - redirect DP_MELDEKORT`() {
            coEvery { meldekortregisterConnector.hentMeldekort(ident) } returns
                listOf(innsendtMeldekort(), meldekortTilUtfylling())

            val response = runBlocking { service.hentMeldekortStatus(ident) }

            response.harInnsendteMeldekort shouldBe true
            response.meldekortTilUtfylling.size shouldBe 1
            response.redirectUrl shouldBe "/arbeid/dagpenger/meldekort"
        }

        @Test
        fun `kun innsendt - redirect DP_MELDEKORT`() {
            coEvery { meldekortregisterConnector.hentMeldekort(ident) } returns listOf(innsendtMeldekort())

            val response = runBlocking { service.hentMeldekortStatus(ident) }

            response.harInnsendteMeldekort shouldBe true
            response.meldekortTilUtfylling shouldBe emptyList()
            response.redirectUrl shouldBe "/arbeid/dagpenger/meldekort"
        }

        @Test
        fun `kun til utfylling - redirect DP_MELDEKORT`() {
            coEvery { meldekortregisterConnector.hentMeldekort(ident) } returns listOf(meldekortTilUtfylling())

            val response = runBlocking { service.hentMeldekortStatus(ident) }

            response.harInnsendteMeldekort shouldBe false
            response.meldekortTilUtfylling.size shouldBe 1
            response.redirectUrl shouldBe "/arbeid/dagpenger/meldekort"
        }
    }

    @Nested
    inner class HentFraArena {
        init {
            coEvery { meldekortregisterConnector.hentMeldekort(ident) } returns emptyList()
        }

        @Test
        fun `innsendt og til utfylling - redirect DP_MELDEKORT`() {
            coEvery { meldepliktConnector.hentMeldekortFraArena(ident) } returns
                listOf(arenaMeldekortInnsendt(), arenaMeldekortTilUtfylling())

            val response = runBlocking { service.hentMeldekortStatus(ident) }

            response.harInnsendteMeldekort shouldBe true
            response.meldekortTilUtfylling.size shouldBe 1
            response.redirectUrl shouldBe "/arbeid/dagpenger/meldekort"
        }

        @Test
        fun `kun innsendt - redirect DP_MELDEKORT`() {
            coEvery { meldepliktConnector.hentMeldekortFraArena(ident) } returns listOf(arenaMeldekortInnsendt())

            val response = runBlocking { service.hentMeldekortStatus(ident) }

            response.harInnsendteMeldekort shouldBe true
            response.meldekortTilUtfylling shouldBe emptyList()
            response.redirectUrl shouldBe "/arbeid/dagpenger/meldekort"
        }

        @Test
        fun `kun til utfylling - redirect DP_MELDEKORT`() {
            coEvery { meldepliktConnector.hentMeldekortFraArena(ident) } returns listOf(arenaMeldekortTilUtfylling())

            val response = runBlocking { service.hentMeldekortStatus(ident) }

            response.harInnsendteMeldekort shouldBe false
            response.meldekortTilUtfylling.size shouldBe 1
            response.redirectUrl shouldBe "/arbeid/dagpenger/meldekort"
        }

        @Test
        fun `ingen innsendt og ingen til utfylling - redirect FELLES_MELDEKORT`() {
            coEvery { meldepliktConnector.hentMeldekortFraArena(ident) } returns
                listOf(ArenaMeldekortResponse("Ferdig", idag.minusDays(14)))

            val response = runBlocking { service.hentMeldekortStatus(ident) }

            response.harInnsendteMeldekort shouldBe false
            response.meldekortTilUtfylling shouldBe emptyList()
            response.redirectUrl shouldBe "/felles-meldekort"
        }
    }

    @Nested
    inner class Fallback {
        @Test
        fun `meldekortregister tom - henter fra Arena`() {
            coEvery { meldekortregisterConnector.hentMeldekort(ident) } returns emptyList()
            coEvery { meldepliktConnector.hentMeldekortFraArena(ident) } returns listOf(arenaMeldekortTilUtfylling())

            val response = runBlocking { service.hentMeldekortStatus(ident) }

            response.meldekortTilUtfylling.size shouldBe 1
            response.redirectUrl shouldBe "/arbeid/dagpenger/meldekort"
        }

        @Test
        fun `begge tomme - tom default respons`() {
            coEvery { meldekortregisterConnector.hentMeldekort(ident) } returns emptyList()
            coEvery { meldepliktConnector.hentMeldekortFraArena(ident) } returns emptyList()

            val response = runBlocking { service.hentMeldekortStatus(ident) }

            response.harInnsendteMeldekort shouldBe false
            response.meldekortTilUtfylling shouldBe emptyList()
            response.redirectUrl shouldBe ""
        }

        @Test
        fun `meldekortregister krasjer - kaster exception`() {
            coEvery { meldekortregisterConnector.hentMeldekort(ident) } throws RuntimeException("utilgjengelig")

            shouldThrow<RuntimeException> {
                runBlocking { service.hentMeldekortStatus(ident) }
            }
        }

        @Test
        fun `Arena krasjer - kaster exception`() {
            coEvery { meldekortregisterConnector.hentMeldekort(ident) } returns emptyList()
            coEvery { meldepliktConnector.hentMeldekortFraArena(ident) } throws RuntimeException("utilgjengelig")

            shouldThrow<RuntimeException> {
                runBlocking { service.hentMeldekortStatus(ident) }
            }
        }
    }

    private fun innsendtMeldekort() = MeldekortResponse("Innsendt", idag.minusDays(14), idag.minusDays(7))

    private fun meldekortTilUtfylling() = MeldekortResponse("TilUtfylling", idag, idag.plusDays(7))

    private fun arenaMeldekortInnsendt() = ArenaMeldekortResponse("Innsendt", idag.minusDays(14))

    private fun arenaMeldekortTilUtfylling() = ArenaMeldekortResponse("TilUtfylling", idag)
}
