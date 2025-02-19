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
        fun `behandler søknad hendelse for ny bruker`() =
            testPerson {
                behandle(søknadHendelse())
                behandle(startetArbeidssøkerperiodeHendelse())

                status shouldBe Dagpengerbruker
                arbeidssøkerperioder.gjeldende?.overtattBekreftelse shouldBe true
                arbeidssøkerperiodeObserver skalHaSendtOvertakelseFor this
            }

        @Test
        fun `behandler søknad hendelse for dagpengerbruker`() =
            testPerson {
                behandle(søknadHendelse(tidligere, "123"))
                behandle(startetArbeidssøkerperiodeHendelse())

                status shouldBe Dagpengerbruker

                behandle(søknadHendelse(nå, "456"))

                status shouldBe Dagpengerbruker
                arbeidssøkerperioder.gjeldende?.overtattBekreftelse shouldBe true
                arbeidssøkerperiodeObserver skalHaSendtOvertakelseFor this
            }
    }

    @Nested
    inner class DagpengerHendelser {
        @Test
        fun `behandler dagpengermeldegruppe hendelse for ny bruker`() =
            arbeidssøker {
                status shouldBe IkkeDagpengerbruker

                behandle(dagpengerMeldegruppeHendelse())

                status shouldBe Dagpengerbruker
                arbeidssøkerperioder.gjeldende?.overtattBekreftelse shouldBe true
                arbeidssøkerperiodeObserver skalHaSendtOvertakelseFor this
            }

        @Test
        fun `dagpengerhendelse endrer ikke allerede Dagpengerbruker status`() =
            arbeidssøker(overtattBekreftelse = true) {
                statusHistorikk.put(tidligere, Dagpengerbruker)

                behandle(dagpengerMeldegruppeHendelse(nå))
                status shouldBe Dagpengerbruker
                arbeidssøkerperioder.gjeldende?.overtattBekreftelse shouldBe true
                arbeidssøkerperiodeObserver skalIkkeHaSendtOvertakelseFor this
            }

        @Test
        fun `dagpengerhendelse gir Dagpengerbruker status til IkkeDagpengerbruker`() =
            arbeidssøker {
                statusHistorikk.put(tidligere, IkkeDagpengerbruker)

                behandle(dagpengerMeldegruppeHendelse(nå))

                status shouldBe Dagpengerbruker
                arbeidssøkerperioder.gjeldende?.overtattBekreftelse shouldBe true
                arbeidssøkerperiodeObserver skalHaSendtOvertakelseFor this
            }
    }

    @Nested
    inner class AnnenMeldegruppeHendelser {
        @Test
        fun `annen meldegruppe hendelse gir IkkeDagpengerbruker`() =
            arbeidssøker(overtattBekreftelse = true) {
                behandle(søknadHendelse())
                behandle(annenMeldegruppeHendelse())

                status shouldBe IkkeDagpengerbruker
                arbeidssøkerperioder.gjeldende?.overtattBekreftelse shouldBe false
                arbeidssøkerperiodeObserver skalHaFrasagtAnsvaretFor this
            }

        @Test
        fun `IkkeDagpengerbruker status forblir samme med annen meldegruppe hendelse`() =
            arbeidssøker(overtattBekreftelse = true) {
                behandle(søknadHendelse())
                behandle(annenMeldegruppeHendelse())
                status shouldBe IkkeDagpengerbruker

                behandle(annenMeldegruppeHendelse())
                status shouldBe IkkeDagpengerbruker
                arbeidssøkerperioder.gjeldende?.overtattBekreftelse shouldBe false
                arbeidssøkerperiodeObserver skalHaFrasagtAnsvaretFor this
            }
    }

    private fun testPerson(block: Person.() -> Unit) {
        Person(ident)
            .apply { addObserver(arbeidssøkerperiodeObserver) }
            .apply(block)
    }

    private fun arbeidssøker(
        overtattBekreftelse: Boolean = false,
        block: Person.() -> Unit,
    ) {
        Person(ident)
            .apply { addObserver(arbeidssøkerperiodeObserver) }
            .apply {
                arbeidssøkerperioder.add(
                    Arbeidssøkerperiode(
                        UUID.randomUUID(),
                        ident,
                        LocalDateTime.now(),
                        null,
                        overtattBekreftelse = overtattBekreftelse,
                    ),
                )
            }.apply(block)
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

    private fun startetArbeidssøkerperiodeHendelse() = StartetArbeidssøkerperiodeHendelse(UUID.randomUUID(), ident, tidligere)
}
