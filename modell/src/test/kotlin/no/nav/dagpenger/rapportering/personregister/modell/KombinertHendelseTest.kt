package no.nav.dagpenger.rapportering.personregister.modell

import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class KombinertHendelseTest {
    @Test
    fun `should create KombinertHendelse with correct properties`() {
        // Given
        val ident = "12345678901"
        val dato = LocalDateTime.now()
        val referanseId = "test-123-456"
        val meldepliktHendelse =
            MeldepliktHendelse(
                ident = ident,
                dato = dato,
                referanseId = "123",
                startDato = dato,
                sluttDato = null,
                statusMeldeplikt = true,
            )
        val meldegruppeHendelse =
            DagpengerMeldegruppeHendelse(
                ident = ident,
                dato = dato,
                referanseId = "456",
                startDato = dato,
                sluttDato = null,
                meldegruppeKode = "DAGP",
                harMeldtSeg = true,
            )

        // When
        val kombinertHendelse =
            KombinertHendelse(
                ident = ident,
                dato = dato,
                referanseId = referanseId,
                meldepliktHendelser = listOf(meldepliktHendelse),
                meldegruppeHendelser = listOf(meldegruppeHendelse),
            )

        // Then
        kombinertHendelse.ident shouldBe ident
        kombinertHendelse.dato shouldBe dato
        kombinertHendelse.referanseId shouldBe referanseId
        kombinertHendelse.meldepliktHendelser shouldBe listOf(meldepliktHendelse)
        kombinertHendelse.meldegruppeHendelser shouldBe listOf(meldegruppeHendelse)
        kombinertHendelse.kilde shouldBe Kildesystem.Dagpenger
    }

    @Test
    fun `should process all hendelser when behandle is called`() {
        // Given
        val ident = "12345678901"
        val dato = LocalDateTime.now()

        // Create mock hendelser
        val meldepliktHendelse = mockk<MeldepliktHendelse>(relaxed = true)
        val meldegruppeHendelse = mockk<DagpengerMeldegruppeHendelse>(relaxed = true)

        val kombinertHendelse =
            KombinertHendelse(
                ident = ident,
                dato = dato,
                referanseId = "test-123-456",
                meldepliktHendelser = listOf(meldepliktHendelse),
                meldegruppeHendelser = listOf(meldegruppeHendelse),
            )

        // Create a mock person
        val person = mockk<Person>(relaxed = true)

        // When
        kombinertHendelse.behandle(person)

        // Then
        verify(exactly = 1) { meldepliktHendelse.behandle(person) }
        verify(exactly = 1) { meldegruppeHendelse.behandle(person) }
    }

    @Test
    fun `should handle multiple hendelser of each type`() {
        // Given
        val ident = "12345678901"
        val dato = LocalDateTime.now()

        // Create mock hendelser
        val meldepliktHendelse1 = mockk<MeldepliktHendelse>(relaxed = true)
        val meldepliktHendelse2 = mockk<MeldepliktHendelse>(relaxed = true)
        val meldegruppeHendelse1 = mockk<DagpengerMeldegruppeHendelse>(relaxed = true)
        val meldegruppeHendelse2 = mockk<AnnenMeldegruppeHendelse>(relaxed = true)

        val kombinertHendelse =
            KombinertHendelse(
                ident = ident,
                dato = dato,
                referanseId = "test-123-456",
                meldepliktHendelser = listOf(meldepliktHendelse1, meldepliktHendelse2),
                meldegruppeHendelser = listOf(meldegruppeHendelse1, meldegruppeHendelse2),
            )

        // Create a mock person
        val person = mockk<Person>(relaxed = true)

        // When
        kombinertHendelse.behandle(person)

        // Then
        verify(exactly = 1) { meldepliktHendelse1.behandle(person) }
        verify(exactly = 1) { meldepliktHendelse2.behandle(person) }
        verify(exactly = 1) { meldegruppeHendelse1.behandle(person) }
        verify(exactly = 1) { meldegruppeHendelse2.behandle(person) }
    }
}
