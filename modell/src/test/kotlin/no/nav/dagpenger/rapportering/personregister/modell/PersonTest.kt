package no.nav.dagpenger.rapportering.personregister.modell

import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class PersonTest {
    private val ident = "12345678901"
    private val nå = LocalDateTime.now()
    private val tidligere = nå.minusDays(1)

    private val arbeidssøkerperiodeObserver = mockk<PersonObserver>(relaxed = true)

    @Nested
    inner class SøknadHendelser {
        @Test
        fun `håndterer søknad hendelse for ny bruker`() =
            testPerson {
                behandle(søknadHendelse())

                status shouldBe Dagpengerbruker
                gjeldendeArbeidssøkerperiode?.overtattBekreftelse shouldBe true
                arbeidssøkerperiodeObserver skalHaSendtOvertakelseFor this
            }

        @Test
        fun `håndterer søknad hendelse for dagpengerbruker`() =
            testPerson {
                behandle(søknadHendelse(tidligere, "123"))
                status shouldBe Dagpengerbruker

                behandle(søknadHendelse(nå, "456"))

                status shouldBe Dagpengerbruker
                gjeldendeArbeidssøkerperiode?.overtattBekreftelse shouldBe true
                arbeidssøkerperiodeObserver skalHaSendtOvertakelseFor this
            }
    }

    @Nested
    inner class DagpengerHendelser {
        @Test
        fun `håndterer dagpengermeldegruppe hendelse for ny bruker`() =
            testPerson {
                status shouldBe IkkeDagpengerbruker

                behandle(dagpengerMeldegruppeHendelse())

                status shouldBe Dagpengerbruker
                gjeldendeArbeidssøkerperiode?.overtattBekreftelse shouldBe true
                arbeidssøkerperiodeObserver skalHaSendtOvertakelseFor this
            }

        @Test
        fun `dagpengerhendelse endrer ikke allerede Dagpengerbruker status`() =
            testPerson {
                behandle(søknadHendelse(tidligere))
                status shouldBe Dagpengerbruker

                behandle(dagpengerMeldegruppeHendelse(nå))
                status shouldBe Dagpengerbruker
                gjeldendeArbeidssøkerperiode?.overtattBekreftelse shouldBe true
                arbeidssøkerperiodeObserver skalHaFrasagtAnsvaretFor this
            }

        @Test
        fun `dagpengerhendelse gir Dagpengerbruker status til IkkeDagpengerbruker`() =
            testPerson {
                behandle(annenMeldegruppeHendelse(tidligere))
                status shouldBe IkkeDagpengerbruker

                behandle(dagpengerMeldegruppeHendelse(nå))

                status shouldBe Dagpengerbruker
                gjeldendeArbeidssøkerperiode?.overtattBekreftelse shouldBe true
                arbeidssøkerperiodeObserver skalHaSendtOvertakelseFor this
            }
    }

    @Nested
    inner class AnnenMeldegruppeHendelser {
        @Test
        fun `annen meldegruppe hendelse gir IkkeDagpengerbruker`() =
            testPerson {
                behandle(søknadHendelse())
                behandle(annenMeldegruppeHendelse())

                status shouldBe IkkeDagpengerbruker
                gjeldendeArbeidssøkerperiode?.overtattBekreftelse shouldBe false
                arbeidssøkerperiodeObserver skalHaFrasagtAnsvaretFor this
            }

        @Test
        fun `IkkeDagpengerbruker status forblir samme med annen meldegruppe hendelse`() =
            testPerson {
                behandle(søknadHendelse())
                behandle(annenMeldegruppeHendelse())
                status shouldBe IkkeDagpengerbruker

                behandle(annenMeldegruppeHendelse())
                status shouldBe IkkeDagpengerbruker
                gjeldendeArbeidssøkerperiode?.overtattBekreftelse shouldBe false
                arbeidssøkerperiodeObserver skalHaFrasagtAnsvaretFor this
            }
    }

    private fun testPerson(block: Person.() -> Unit) {
        Person(ident)
            .apply { addObserver(arbeidssøkerperiodeObserver) }
            .apply { aktivArbeidssøkerperiodeHendelse().håndter(this) }
            .apply(block)
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

    private fun aktivArbeidssøkerperiodeHendelse(
        dato: LocalDateTime = nå,
        referanseId: String = "123",
    ) = StartetArbeidssøkerperiodeHendelse(UUID.randomUUID(), ident, tidligere)
}
