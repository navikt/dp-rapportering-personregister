package no.nav.dagpenger.rapportering.personregister.modell

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class StatusTest {
    private val ident = "12345678901"
    private val nå = LocalDateTime.now()
    private val tidligere = nå.minusDays(1)

    @Nested
    inner class SøknadHendelser {
        @Test
        fun `søknad gir aktiv status`() =
            testPerson {
                behandle(søknadHendelse())
                status shouldBe Dagpengerbruker
            }

        @Test
        fun `ny søknad beholder aktiv status`() =
            testPerson {
                behandle(søknadHendelse(tidligere, "123"))
                status shouldBe Dagpengerbruker

                behandle(søknadHendelse(nå, "456"))
                status shouldBe Dagpengerbruker
            }
    }

    @Nested
    inner class DagpengerHendelser {
        @Test
        fun `dagpengerhendelse gir aktiv status for ny bruker`() =
            testPerson {
                status shouldBe IkkeDagpengerbruker
                behandle(dagpengerMeldegruppeHendelse())
                status shouldBe Dagpengerbruker
            }

        @Test
        fun `dagpengerhendelse endrer ikke allerede aktiv status`() =
            testPerson {
                behandle(søknadHendelse(tidligere))
                status shouldBe Dagpengerbruker
                behandle(dagpengerMeldegruppeHendelse(nå))
                status shouldBe Dagpengerbruker
            }

        @Test
        fun `dagpengerhendelse gir aktiv status til inaktiv bruker`() =
            testPerson {
                behandle(annenMeldegruppeHendelse(tidligere))
                status shouldBe IkkeDagpengerbruker
                behandle(dagpengerMeldegruppeHendelse(nå))
                status shouldBe Dagpengerbruker
            }
    }

    @Nested
    inner class AnnenMeldegruppeHendelser {
        @Test
        fun `annen meldegruppe hendelse gir inaktiv status`() =
            testPerson {
                behandle(annenMeldegruppeHendelse())
                status shouldBe IkkeDagpengerbruker
            }

        @Test
        fun `aktiv status blir inaktiv med annen meldegruppe hendelse`() =
            testPerson {
                behandle(søknadHendelse())
                status shouldBe Dagpengerbruker
                behandle(annenMeldegruppeHendelse())
                status shouldBe IkkeDagpengerbruker
            }

        @Test
        fun `inaktiv status forblir inaktiv med annen meldegruppe hendelse`() =
            testPerson {
                behandle(annenMeldegruppeHendelse())
                status shouldBe IkkeDagpengerbruker
                behandle(annenMeldegruppeHendelse())
                status shouldBe IkkeDagpengerbruker
            }
    }

    private fun testPerson(block: Person.() -> Unit) {
        Person(ident).apply(block)
    }

    private fun søknadHendelse(
        dato: LocalDateTime = nå,
        referanseId: String = "123",
    ) = SøknadHendelse(ident, dato, referanseId)

    private fun dagpengerMeldegruppeHendelse(
        dato: LocalDateTime = nå,
        referanseId: String = "123",
    ) = DagpengerMeldegruppeHendelse(ident, dato, "DAGP", referanseId)

    private fun annenMeldegruppeHendelse(
        dato: LocalDateTime = nå,
        referanseId: String = "123",
    ) = AnnenMeldegruppeHendelse(ident, dato, "ARBS", referanseId)
}
