package no.nav.dagpenger.rapportering.personregister.modell

import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.mockk.verify
import no.nav.dagpenger.rapportering.personregister.modell.Status.DAGPENGERBRUKER
import no.nav.dagpenger.rapportering.personregister.modell.Status.IKKE_DAGPENGERBRUKER
import no.nav.dagpenger.rapportering.personregister.modell.helper.annenMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.helper.dagpengerMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.AvsluttetArbeidssøkerperiodeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.MeldepliktHendelse
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.MeldesyklusErPassertHendelse
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.StartetArbeidssøkerperiodeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.SøknadHendelse
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.VedtakHendelse
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class PersonTest {
    private val ident = "12345678901"
    private val nå = LocalDateTime.now()
    private val senere = nå.plusDays(1)
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
        fun `behandler søknad hendelse for bruker som oppfyller kravet`() =
            arbeidssøker {
                behandle(meldepliktHendelse(status = true))
                behandle(dagpengerMeldegruppeHendelse())
                behandle(søknadHendelse())

                status shouldBe DAGPENGERBRUKER
                arbeidssøkerperiodeObserver skalHaSendtOvertakelseFor this
            }
    }

    @Nested
    inner class VedtakHendelser {
        @Test
        fun `behandler vedtak hendelse`() =
            arbeidssøker {
                val søknadId = "456"
                hendelser.add(søknadHendelse(referanseId = søknadId))
                behandle(vedtakHendelse(søknadId = søknadId))

                // TODO: ansvarligSystem shouldBe AnsvarligSystem.DP når vi har dp-meldekortregister
                ansvarligSystem shouldBe AnsvarligSystem.ARENA
                this skalHaSendtStartMeldingFor nå
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
        fun `behandler dagpengermeldegruppe hendelse for bruker som oppfyller kravet`() =
            arbeidssøker {
                behandle(meldepliktHendelse(status = true))
                behandle(dagpengerMeldegruppeHendelse())

                status shouldBe DAGPENGERBRUKER
                arbeidssøkerperiodeObserver skalHaSendtOvertakelseFor this
            }
    }

    @Nested
    inner class AnnenMeldegruppeHendelser {
        @Test
        fun `behandler AnnenMeldegruppeHendelse for bruker som vi allerede har tatt ansvar for arbeidssøkerbekreftelse`() =
            arbeidssøker(overtattBekreftelse = true) {
                behandle(meldepliktHendelse(status = true))
                behandle(dagpengerMeldegruppeHendelse(startDato = nå))
                behandle(annenMeldegruppeHendelse(startDato = senere))

                status shouldBe IKKE_DAGPENGERBRUKER
                arbeidssøkerperiodeObserver skalHaFrasagtAnsvaretFor this
            }

        @Test
        fun `behandler AnnenMeldegruppeHendelse for bruker som vi ikke har ansvar for arbeidssøkerbekreftelse`() =
            arbeidssøker(overtattBekreftelse = false) {
                setMeldeplikt(true)
                statusHistorikk.put(tidligere, DAGPENGERBRUKER)

                behandle(annenMeldegruppeHendelse())

                status shouldBe IKKE_DAGPENGERBRUKER
                arbeidssøkerperioder.gjeldende?.overtattBekreftelse shouldBe false
            }
    }

    @Nested
    inner class MeldepliktHendelser {
        @Test
        fun `behandler MeldepliktHendelse for bruker som oppfyller kravet`() =
            arbeidssøker(overtattBekreftelse = false) {
                setMeldegruppe("DAGP")

                behandle(meldepliktHendelse(status = true))

                status shouldBe DAGPENGERBRUKER
                arbeidssøkerperiodeObserver skalHaSendtOvertakelseFor this
            }

        @Test
        fun `behandler MeldepliktHendelse for bruker som ikke oppfyller kravet`() =
            arbeidssøker(overtattBekreftelse = false) {
                setMeldegruppe("ARBS")

                behandle(meldepliktHendelse(status = true))

                status shouldBe IKKE_DAGPENGERBRUKER
                arbeidssøkerperiodeObserver skalIkkeHaSendtOvertakelseFor this
            }

        @Test
        fun `behandler MeldepliktHendelse for Dagpengerbruker som ikke lenger oppfyller kravet `() =
            arbeidssøker {
                behandle(meldepliktHendelse(status = true))
                behandle(dagpengerMeldegruppeHendelse(startDato = nå))

                behandle(annenMeldegruppeHendelse(startDato = senere))
                behandle(meldepliktHendelse(status = true))

                status shouldBe IKKE_DAGPENGERBRUKER
            }
    }

    @Nested
    inner class ArbeidsøkerperiodeHendelser {
        @Test
        fun `behandler startet arbeidssøker hendelser for bruker som ikke oppfyller kravet`() =
            testPerson {
                behandle(startetArbeidssøkerperiodeHendelse())

                status shouldBe IKKE_DAGPENGERBRUKER
//                arbeidssøkerperioder.gjeldende?.overtattBekreftelse shouldBe null
//                arbeidssøkerperiodeObserver skalIkkeHaSendtOvertakelseFor this
            }

        @Test
        fun `behandler StartetArbeidssøkerperiodeHendelse for bruker som oppfyller kravet`() =
            testPerson {
                behandle(meldepliktHendelse(status = true))
                behandle(dagpengerMeldegruppeHendelse())
                behandle(startetArbeidssøkerperiodeHendelse())

                status shouldBe DAGPENGERBRUKER
                arbeidssøkerperiodeObserver skalHaSendtOvertakelseFor this
            }

        @Test
        fun `behandler avsluttet arbeidssøker hendelser for Dagpengerbruker`() =
            arbeidssøker(overtattBekreftelse = true) {
                behandle(meldepliktHendelse(status = true))
                behandle(dagpengerMeldegruppeHendelse())
                behandle(avsluttetArbeidssøkerperiodeHendelse())

                status shouldBe IKKE_DAGPENGERBRUKER
                arbeidssøkerperioder.find { it.periodeId == periodeId }?.overtattBekreftelse shouldBe false
            }
    }

    @Nested
    inner class MeldesyklusErPassertHendelser {
        @Test
        fun `behandler meldesyklus er passert hendelse`() =
            arbeidssøker(overtattBekreftelse = true) {
                behandle(meldesyklusErPassertHendelse())

                arbeidssøkerperiodeObserver skalHaFrasagtSegAnsvaretMedFristBruttFor this
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
    ) = SøknadHendelse(ident, dato, dato, referanseId)

    private fun vedtakHendelse(
        dato: LocalDateTime = nå,
        referanseId: String = "123",
        søknadId: String = "456",
        utfall: Boolean = true,
    ) = VedtakHendelse(ident, dato, dato, referanseId, søknadId, utfall)

    private fun meldepliktHendelse(
        dato: LocalDateTime = nå,
        status: Boolean = false,
    ) = MeldepliktHendelse(ident, dato, "123", dato.plusDays(1), null, status, true)

    private fun startetArbeidssøkerperiodeHendelse() = StartetArbeidssøkerperiodeHendelse(UUID.randomUUID(), ident, tidligere)

    private fun avsluttetArbeidssøkerperiodeHendelse() = AvsluttetArbeidssøkerperiodeHendelse(periodeId, ident, tidligere, nå)

    private fun meldesyklusErPassertHendelse() =
        MeldesyklusErPassertHendelse(
            ident,
            nå,
            nå,
            UUID.randomUUID().toString(),
        )
}

infix fun PersonObserver.skalHaSendtOvertakelseFor(person: Person) {
    verify(exactly = 1) { sendOvertakelsesmelding(person) }
}

infix fun PersonObserver.skalIkkeHaSendtOvertakelseFor(person: Person) {
    verify(exactly = 0) { sendOvertakelsesmelding(person) }
}

infix fun PersonObserver.skalHaFrasagtAnsvaretFor(person: Person) {
    verify(exactly = 1) { sendFrasigelsesmelding(person, fristBrutt = false) }
}

infix fun PersonObserver.skalHaFrasagtSegAnsvaretMedFristBruttFor(person: Person) {
    verify(exactly = 1) { sendFrasigelsesmelding(person, fristBrutt = true) }
}

infix fun Person.skalHaSendtStartMeldingFor(startDato: LocalDateTime) {
    verify(exactly = 1) { sendStartMeldingTilMeldekortregister(startDato) }
}
