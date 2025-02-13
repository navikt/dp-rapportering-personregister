package no.nav.dagpenger.rapportering.personregister.modell

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class DagpengerStatusTest {
    private val ident = "12345678901"
    private val nå = LocalDateTime.now()
    private val tidligere = nå.minusDays(1)

    @Nested
    inner class SøknadHendelser {
        @Test
        fun `søknad gir aktiv status`() =
            testPerson {
                behandle(søknadHendelse())
                dagpengerstatus shouldBe AKTIV
            }

        @Test
        fun `ny søknad beholder aktiv status`() =
            testPerson {
                behandle(søknadHendelse(tidligere, "123"))
                dagpengerstatus shouldBe AKTIV

                behandle(søknadHendelse(nå, "456"))
                dagpengerstatus shouldBe AKTIV
            }
    }

    @Nested
    inner class DagpengerHendelser {
        @Test
        fun `dagpengerhendelse gir aktiv status for ny bruker`() =
            testPerson {
                dagpengerstatus shouldBe INAKTIV
                behandle(dagpengerMeldegruppeHendelse())
                dagpengerstatus shouldBe AKTIV
            }

        @Test
        fun `dagpengerhendelse endrer ikke allerede aktiv status`() =
            testPerson {
                behandle(søknadHendelse(tidligere))
                dagpengerstatus shouldBe AKTIV
                behandle(dagpengerMeldegruppeHendelse(nå))
                dagpengerstatus shouldBe AKTIV
            }

        @Test
        fun `dagpengerhendelse gir aktiv status til inaktiv bruker`() =
            testPerson {
                behandle(annenMeldegruppeHendelse(tidligere))
                dagpengerstatus shouldBe INAKTIV
                behandle(dagpengerMeldegruppeHendelse(nå))
                dagpengerstatus shouldBe AKTIV
            }
    }

    @Nested
    inner class AnnenMeldegruppeHendelser {
        @Test
        fun `annen meldegruppe hendelse gir inaktiv status`() =
            testPerson {
                behandle(annenMeldegruppeHendelse())
                dagpengerstatus shouldBe INAKTIV
            }

        @Test
        fun `aktiv status blir inaktiv med annen meldegruppe hendelse`() =
            testPerson {
                behandle(søknadHendelse())
                dagpengerstatus shouldBe AKTIV
                behandle(annenMeldegruppeHendelse())
                dagpengerstatus shouldBe INAKTIV
            }

        @Test
        fun `inaktiv status forblir inaktiv med annen meldegruppe hendelse`() =
            testPerson {
                behandle(annenMeldegruppeHendelse())
                dagpengerstatus shouldBe INAKTIV
                behandle(annenMeldegruppeHendelse())
                dagpengerstatus shouldBe INAKTIV
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
