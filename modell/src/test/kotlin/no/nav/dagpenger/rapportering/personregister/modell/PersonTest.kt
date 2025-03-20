package no.nav.dagpenger.rapportering.personregister.modell

import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.mockk.verify
import no.nav.dagpenger.rapportering.personregister.modell.Status.DAGPENGERBRUKER
import no.nav.dagpenger.rapportering.personregister.modell.Status.IKKE_DAGPENGERBRUKER
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class PersonTest {
    private val ident = "12345678901"
    private val nå = LocalDateTime.now()
    private val tidligere = nå.minusDays(1)
    private val periodeId = UUID.randomUUID()

    private val arbeidssøkerperiodeObserver = mockk<PersonObserver>(relaxed = true)

    @Nested
    inner class SøknadHendelser {
        @Test
        fun `behandler søknad hendelse for bruker som ikke oppfyller kravet`() =
            testPerson {
                behandle(søknadHendelse())

                status shouldBe IKKE_DAGPENGERBRUKER

                arbeidssøkerperioder.gjeldende?.overtattBekreftelse shouldBe null
                arbeidssøkerperiodeObserver skalIkkeHaSendtOvertakelseFor this
            }

        @Test
        @Disabled
        fun `behandler søknad hendelse for bruker som oppfyller kravet`() =
            arbeidssøker {
                behandle(meldepliktHendelse(status = true))
                behandle(dagpengerMeldegruppeHendelse())
                behandle(søknadHendelse())

                status shouldBe DAGPENGERBRUKER
                arbeidssøkerperioder.gjeldende?.overtattBekreftelse shouldBe true
                arbeidssøkerperiodeObserver skalHaSendtOvertakelseFor this
            }
    }

    @Nested
    inner class DagpengerHendelser {
        @Test
        fun `behandler dagpengermeldegruppe hendelse for bruker som ikke oppfyller kravet`() =
            testPerson {
                behandle(meldepliktHendelse(status = false))
                behandle(dagpengerMeldegruppeHendelse())

                status shouldBe IKKE_DAGPENGERBRUKER
                arbeidssøkerperiodeObserver skalIkkeHaSendtOvertakelseFor this
            }

        @Test
        @Disabled
        fun `behandler dagpengermeldegruppe hendelse for bruker som oppfyller kravet`() =
            arbeidssøker {
                behandle(meldepliktHendelse(status = true))
                behandle(dagpengerMeldegruppeHendelse())

                status shouldBe DAGPENGERBRUKER
                arbeidssøkerperioder.gjeldende?.overtattBekreftelse shouldBe true
                arbeidssøkerperiodeObserver skalHaSendtOvertakelseFor this
            }
    }

    @Nested
    inner class AnnenMeldegruppeHendelser {
        @Test
        @Disabled
        fun `behandler AnnenMeldegruppeHendelse for bruker som vi allerede har tatt ansvar for arbeidssøkerbekreftelse`() =
            arbeidssøker(overtattBekreftelse = true) {
                behandle(meldepliktHendelse(status = true))
                behandle(dagpengerMeldegruppeHendelse())
                behandle(annenMeldegruppeHendelse())

                status shouldBe IKKE_DAGPENGERBRUKER
                arbeidssøkerperioder.gjeldende?.overtattBekreftelse shouldBe false
                arbeidssøkerperiodeObserver skalHaFrasagtAnsvaretFor this
            }

        @Test
        fun `behandler AnnenMeldegruppeHendelse for bruker som vi ikke har ansvar for arbeidssøkerbekreftelse`() =
            arbeidssøker(overtattBekreftelse = false) {
                meldeplikt = true
                statusHistorikk.put(tidligere, DAGPENGERBRUKER)

                behandle(annenMeldegruppeHendelse())

                status shouldBe IKKE_DAGPENGERBRUKER
                arbeidssøkerperioder.gjeldende?.overtattBekreftelse shouldBe false
            }
    }

    @Nested
    inner class MeldepliktHendelser {
        @Test
        @Disabled
        fun `behandler MeldepliktHendelse for bruker som oppfyller kravet`() =
            arbeidssøker(overtattBekreftelse = false) {
                meldegruppe = "DAGP"

                behandle(meldepliktHendelse(status = true))

                status shouldBe DAGPENGERBRUKER
                arbeidssøkerperioder.gjeldende?.overtattBekreftelse shouldBe true
                arbeidssøkerperiodeObserver skalHaSendtOvertakelseFor this
            }

        @Test
        fun `behandler MeldepliktHendelse for bruker som ikke oppfyller kravet`() =
            arbeidssøker(overtattBekreftelse = false) {
                meldegruppe = "ARBS"

                behandle(meldepliktHendelse(status = true))

                status shouldBe IKKE_DAGPENGERBRUKER
                arbeidssøkerperioder.gjeldende?.overtattBekreftelse shouldBe false
                arbeidssøkerperiodeObserver skalIkkeHaSendtOvertakelseFor this
            }

        @Test
        @Disabled
        fun `behandler MeldepliktHendelse for Dagpengerbruker som ikke lenger oppfyller kravet `() =
            arbeidssøker {
                behandle(meldepliktHendelse(status = true))
                behandle(dagpengerMeldegruppeHendelse())

                behandle(annenMeldegruppeHendelse())
                behandle(meldepliktHendelse(status = true))

                status shouldBe IKKE_DAGPENGERBRUKER
                arbeidssøkerperioder.gjeldende?.overtattBekreftelse shouldBe false
                arbeidssøkerperiodeObserver skalHaFrasagtAnsvaretFor this
            }
    }

    @Nested
    inner class ArbeidsøkerperiodeHendelser {
        @Test
        fun `behandler startet arbeidssøker hendelser for bruker som ikke oppfyller kravet`() =
            testPerson {
                behandle(startetArbeidssøkerperiodeHendelse())

                status shouldBe IKKE_DAGPENGERBRUKER
                arbeidssøkerperioder.gjeldende?.overtattBekreftelse shouldBe false
                arbeidssøkerperiodeObserver skalIkkeHaSendtOvertakelseFor this
            }

        @Test
        @Disabled
        fun `behandler StartetArbeidssøkerperiodeHendelse for bruker som oppfyller kravet`() =
            testPerson {
                behandle(meldepliktHendelse(status = true))
                behandle(dagpengerMeldegruppeHendelse())
                behandle(startetArbeidssøkerperiodeHendelse())

                status shouldBe DAGPENGERBRUKER
                arbeidssøkerperioder.gjeldende?.overtattBekreftelse shouldBe true
                arbeidssøkerperiodeObserver skalHaSendtOvertakelseFor this
            }

        @Test
        @Disabled
        fun `behandler avsluttet arbeidssøker hendelser for Dagpengerbruker`() =
            arbeidssøker {
                behandle(meldepliktHendelse(status = true))
                behandle(dagpengerMeldegruppeHendelse())
                behandle(avsluttetArbeidssøkerperiodeHendelse())

                status shouldBe IKKE_DAGPENGERBRUKER
                arbeidssøkerperioder.find { it.periodeId == periodeId }?.overtattBekreftelse shouldBe false
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
                        periodeId,
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
    ) = DagpengerMeldegruppeHendelse(ident, dato, referanseId, dato.plusDays(1), null, "DAGP")

    private fun annenMeldegruppeHendelse(
        dato: LocalDateTime = nå,
        referanseId: String = "123",
    ) = AnnenMeldegruppeHendelse(ident, dato, referanseId, dato.plusDays(1), null, "ARBS")

    private fun meldepliktHendelse(
        dato: LocalDateTime = nå,
        status: Boolean = false,
    ) = MeldepliktHendelse(ident, dato, "123", dato.plusDays(1), null, status)

    private fun startetArbeidssøkerperiodeHendelse() = StartetArbeidssøkerperiodeHendelse(UUID.randomUUID(), ident, tidligere)

    private fun avsluttetArbeidssøkerperiodeHendelse() = AvsluttetArbeidssøkerperiodeHendelse(periodeId, ident, tidligere, nå)
}

infix fun PersonObserver.skalHaSendtOvertakelseFor(person: Person) {
    verify(exactly = 1) { overtaArbeidssøkerBekreftelse(person) }
}

infix fun PersonObserver.skalIkkeHaSendtOvertakelseFor(person: Person) {
    verify(exactly = 0) { overtaArbeidssøkerBekreftelse(person) }
}

infix fun PersonObserver.skalHaFrasagtAnsvaretFor(person: Person) {
    verify(exactly = 1) { frasiArbeidssøkerBekreftelse(person) }
}

infix fun PersonObserver.skalIkkeHaFrasagtAnsvaretFor(person: Person) {
    verify(exactly = 0) { frasiArbeidssøkerBekreftelse(person) }
}
