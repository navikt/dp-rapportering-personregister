package no.nav.dagpenger.rapportering.personregister.modell

import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class ArbeidssøkerperiodeHendelseTest {
    private val arbeidssøkerperiodeObserver = mockk<PersonObserver>(relaxed = true)

    @Test
    fun `sender overtagelsesmelding for startet periode hvis vi ikke har tatt ansvar`() {
        val person = Person("12345678901")
        val periodeId = UUID.randomUUID()

        val startetArbeidssøkerperiodeHendelse = StartetArbeidssøkerperiodeHendelse(periodeId, person.ident, LocalDateTime.now())
        person.addObserver(arbeidssøkerperiodeObserver)

        startetArbeidssøkerperiodeHendelse.håndter(person)

        verify(exactly = 1) { arbeidssøkerperiodeObserver.ovetaArbeidssøkerBekreftelse(person) }
        person.arbeidssøkerperioder.size shouldBe 1
        person.arbeidssøkerperioder.first().startet shouldBe startetArbeidssøkerperiodeHendelse.startet
    }

    @Test
    fun `sender overtagelsesmelding dersom perioden finnes allerede og vi har ikke ansvaret`() {
        val periodeId = UUID.randomUUID()
        val ident = "12345678901"
        val startet = LocalDateTime.now()
        val arbeidssøkerperiode = Arbeidssøkerperiode(periodeId, ident, startet, null, false)

        val person = Person(ident).apply { arbeidssøkerperioder.add(arbeidssøkerperiode) }
        val startetArbeidssøkerperiodeHendelse = StartetArbeidssøkerperiodeHendelse(periodeId, person.ident, startet)

        person.addObserver(arbeidssøkerperiodeObserver)

        startetArbeidssøkerperiodeHendelse.håndter(person)

        verify(exactly = 1) { arbeidssøkerperiodeObserver.ovetaArbeidssøkerBekreftelse(person) }
        person.arbeidssøkerperioder.size shouldBe 1
        person.arbeidssøkerperioder.first().startet shouldBe startetArbeidssøkerperiodeHendelse.startet
    }

    @Test
    fun `sender ikke overtagelsesmelding for avsluttet periode`() {
        val person = Person("12345678901")
        val periodeId = UUID.randomUUID()
        val startet = LocalDateTime.now().minusDays(1)
        val avsluttet = LocalDateTime.now()

        val avsluttetArbeidssøkerperiodeHendelse =
            AvsluttetArbeidssøkerperiodeHendelse(
                periodeId,
                person.ident,
                startet,
                avsluttet,
            )
        person.addObserver(arbeidssøkerperiodeObserver)

        avsluttetArbeidssøkerperiodeHendelse.håndter(person)

        verify(exactly = 0) { arbeidssøkerperiodeObserver.ovetaArbeidssøkerBekreftelse(person) }
        person.arbeidssøkerperioder.size shouldBe 1
        person.arbeidssøkerperioder.first().avsluttet shouldBe avsluttet
    }
}
