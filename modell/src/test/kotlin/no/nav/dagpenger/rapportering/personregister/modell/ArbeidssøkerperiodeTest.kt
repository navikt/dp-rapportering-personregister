package no.nav.dagpenger.rapportering.personregister.modell

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class ArbeidssøkerperiodeTest {
    private val arbeidssøkerperiodeObserver = mockk<PersonObserver>(relaxed = true)
    private lateinit var person: Person

    @BeforeEach
    fun setup() {
        person =
            Person("12345678901")
                .apply { addObserver(arbeidssøkerperiodeObserver) }
    }

    @Nested
    inner class StartetArbeidssøkerperiode {
        @Test
        fun `overtar ansvar for ny periode`() {
            val periodeId = UUID.randomUUID()
            val hendelse = StartetArbeidssøkerperiodeHendelse(periodeId, person.ident, LocalDateTime.now())

            hendelse.håndter(person)

            arbeidssøkerperiodeObserver skalHaSendtOvertakelseFor person
            person.arbeidssøkerperioder shouldHaveSize 1
            person.arbeidssøkerperioder.first().periodeId shouldBe periodeId
            person.arbeidssøkerperioder.first().overtattBekreftelse shouldBe true
        }

        @Test
        fun `overtar ansvar for eksisterende periode hvis vi ikke har ansvaret`() {
            val periodeId = UUID.randomUUID()
            val startet = LocalDateTime.now()
            person.arbeidssøkerperioder += Arbeidssøkerperiode(periodeId, person.ident, startet, null, false)

            val hendelse = StartetArbeidssøkerperiodeHendelse(periodeId, person.ident, startet)

            hendelse.håndter(person)

            arbeidssøkerperiodeObserver skalHaSendtOvertakelseFor person
            person.arbeidssøkerperioder shouldHaveSize 1
            person.arbeidssøkerperioder.first().periodeId shouldBe periodeId
            person.arbeidssøkerperioder.first().overtattBekreftelse shouldBe true
        }

        @Test
        fun `overtar ikke ansvar hvis perioden allerede er vår`() {
            val periodeId = UUID.randomUUID()
            val startet = LocalDateTime.now()
            person.arbeidssøkerperioder += Arbeidssøkerperiode(periodeId, person.ident, startet, null, true)

            val hendelse = StartetArbeidssøkerperiodeHendelse(periodeId, person.ident, startet)

            hendelse.håndter(person)

            arbeidssøkerperiodeObserver skalIkkeHaSendtOvertakelseFor person
            person.arbeidssøkerperioder shouldHaveSize 1
            person.arbeidssøkerperioder.first().periodeId shouldBe periodeId
            person.arbeidssøkerperioder.first().overtattBekreftelse shouldBe true
        }
    }

    @Nested
    inner class AvsluttetArbeidssøkerperiode {
        @Test
        fun `legger til en avsluttet periode`() {
            val periodeId = UUID.randomUUID()
            val startet = LocalDateTime.now().minusDays(1)
            val avsluttet = LocalDateTime.now()
            val hendelse = AvsluttetArbeidssøkerperiodeHendelse(periodeId, person.ident, startet, avsluttet)

            hendelse.håndter(person)

            arbeidssøkerperiodeObserver skalIkkeHaSendtOvertakelseFor person
            person.arbeidssøkerperioder shouldHaveSize 1
            person.arbeidssøkerperioder.first().periodeId shouldBe periodeId
            person.arbeidssøkerperioder.first().overtattBekreftelse shouldBe false
        }

        @Test
        fun `setter periode som avsluttet`() {
            val periodeId = UUID.randomUUID()
            val startet = LocalDateTime.now().minusDays(1)
            val avsluttet = LocalDateTime.now()
            val hendelse = AvsluttetArbeidssøkerperiodeHendelse(periodeId, person.ident, startet, avsluttet)

            hendelse.håndter(person)

            arbeidssøkerperiodeObserver skalIkkeHaSendtOvertakelseFor person
            person.arbeidssøkerperioder shouldHaveSize 1
            person.arbeidssøkerperioder.first().periodeId shouldBe periodeId
            person.arbeidssøkerperioder.first().overtattBekreftelse shouldBe false
        }

        @Test
        fun `ignorerer hvis perioden allerede er avsluttet`() {
            val periodeId = UUID.randomUUID()
            val startet = LocalDateTime.now().minusDays(1)
            val avsluttet = LocalDateTime.now()
            val hendelse = AvsluttetArbeidssøkerperiodeHendelse(periodeId, person.ident, startet, avsluttet)

            hendelse.håndter(person)

            arbeidssøkerperiodeObserver skalIkkeHaSendtOvertakelseFor person
            person.arbeidssøkerperioder shouldHaveSize 1
            person.arbeidssøkerperioder.first().periodeId shouldBe periodeId
            person.arbeidssøkerperioder.first().overtattBekreftelse shouldBe false
        }
    }
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
