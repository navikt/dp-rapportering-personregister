package no.nav.dagpenger.rapportering.personregister.mediator.observers

import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDateTime
import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.verify
import no.nav.dagpenger.rapportering.personregister.mediator.ApplicationBuilder
import no.nav.dagpenger.rapportering.personregister.mediator.ApplicationBuilder.Companion.getRapidsConnection
import no.nav.dagpenger.rapportering.personregister.mediator.db.MeldingerRepository
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.modell.Person
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class PersonObserverMeldekortregisterTest {
    val meldingerRepository = mockk<MeldingerRepository>(relaxed = true)

    @Test
    fun `kan sende Start-melding`() {
        val testRapid = TestRapid()
        mockkObject(ApplicationBuilder.Companion)
        every { getRapidsConnection() } returns testRapid

        val personId = 1234L
        val ident = "12345678910"
        val person = Person(ident)
        val startDato = LocalDateTime.now().minusDays(1)

        val personRepository = mockk<PersonRepository>(relaxed = true)
        every { personRepository.hentPersonId(eq(ident)) } returns personId

        val personObserverMeldekortregister = PersonObserverMeldekortregister(personRepository, meldingerRepository)

        personObserverMeldekortregister.sendStartMeldingTilMeldekortregister(person, startDato, null, true)

        testRapid.inspektør.size shouldBe 1

        val melding = testRapid.inspektør.message(0)
        melding["@event_name"].asString() shouldBe "meldekortoppretting"
        melding["personId"].asLong() shouldBe personId
        melding["ident"].asString() shouldBe ident
        melding["fraOgMed"].asLocalDateTime() shouldBe startDato
        melding["tilOgMed"] shouldBe null
        melding["harRett"].asBoolean() shouldBe true
        melding["handling"].asString() shouldBe "START"
        melding["referanseId"].asString() shouldNotBe null
        melding["skalMigreres"].asBoolean() shouldBe true

        verify(exactly = 1) {
            meldingerRepository.lagreUtgåendeMelding(
                any(),
                ident,
                melding.toString(),
            )
        }
    }

    @Test
    fun `kan sende Start-melding med tilOgMed`() {
        val testRapid = TestRapid()
        mockkObject(ApplicationBuilder.Companion)
        every { getRapidsConnection() } returns testRapid

        val personId = 1234L
        val ident = "12345678910"
        val person = Person(ident)
        val fraOgMed = LocalDateTime.now().minusDays(1)
        val tilOgMed = LocalDateTime.now().plusDays(10)

        val personRepository = mockk<PersonRepository>(relaxed = true)
        every { personRepository.hentPersonId(eq(ident)) } returns personId

        val personObserverMeldekortregister = PersonObserverMeldekortregister(personRepository, meldingerRepository)

        personObserverMeldekortregister.sendStartMeldingTilMeldekortregister(person, fraOgMed, tilOgMed, false)

        testRapid.inspektør.size shouldBe 1

        val melding = testRapid.inspektør.message(0)
        melding["@event_name"].asString() shouldBe "meldekortoppretting"
        melding["personId"].asLong() shouldBe personId
        melding["ident"].asString() shouldBe ident
        melding["fraOgMed"].asLocalDateTime() shouldBe fraOgMed
        melding["tilOgMed"].asLocalDateTime() shouldBe tilOgMed
        melding["harRett"].asBoolean() shouldBe true
        melding["handling"].asString() shouldBe "START"
        melding["referanseId"].asString() shouldNotBe null
        melding["skalMigreres"].asBoolean() shouldBe false

        verify(exactly = 1) {
            meldingerRepository.lagreUtgåendeMelding(
                any(),
                ident,
                melding.toString(),
            )
        }
    }

    @Test
    fun `kan sende Stopp-melding med harRett true`() {
        val testRapid = TestRapid()
        mockkObject(ApplicationBuilder.Companion)
        every { getRapidsConnection() } returns testRapid

        val personId = 1234L
        val ident = "12345678910"
        val person = Person(ident)
        val fraOgMed = LocalDateTime.now().minusDays(2)
        val tilOgMed = LocalDateTime.now().plusDays(10)

        val personRepository = mockk<PersonRepository>(relaxed = true)
        every { personRepository.hentPersonId(eq(ident)) } returns personId

        val personObserverMeldekortregister = PersonObserverMeldekortregister(personRepository, meldingerRepository)

        personObserverMeldekortregister.sendStoppMeldingTilMeldekortregister(
            person = person,
            fraOgMed = fraOgMed,
            tilOgMed = tilOgMed,
            harRett = true,
        )

        testRapid.inspektør.size shouldBe 1

        val melding = testRapid.inspektør.message(0)
        melding["@event_name"].asString() shouldBe "meldekortoppretting"
        melding["personId"].asLong() shouldBe personId
        melding["ident"].asString() shouldBe ident
        melding["fraOgMed"].asLocalDateTime() shouldBe fraOgMed
        melding["tilOgMed"].asLocalDateTime() shouldBe tilOgMed
        melding["harRett"].asBoolean() shouldBe true
        melding["handling"].asString() shouldBe "STOPP"
        melding["referanseId"].asString() shouldNotBe null
        melding["skalMigreres"].asBoolean() shouldBe false

        verify(exactly = 1) {
            meldingerRepository.lagreUtgåendeMelding(
                any(),
                ident,
                melding.toString(),
            )
        }
    }

    @Test
    fun `kan sende Stopp-melding med harRett false`() {
        val testRapid = TestRapid()
        mockkObject(ApplicationBuilder.Companion)
        every { getRapidsConnection() } returns testRapid

        val personId = 1234L
        val ident = "12345678910"
        val person = Person(ident)
        val fraOgMed = LocalDateTime.now().minusDays(2)
        val tilOgMed = LocalDateTime.now().plusDays(10)

        val personRepository = mockk<PersonRepository>(relaxed = true)
        every { personRepository.hentPersonId(eq(ident)) } returns personId

        val personObserverMeldekortregister = PersonObserverMeldekortregister(personRepository, meldingerRepository)

        personObserverMeldekortregister.sendStoppMeldingTilMeldekortregister(
            person = person,
            fraOgMed = fraOgMed,
            tilOgMed = tilOgMed,
            harRett = false,
        )

        testRapid.inspektør.size shouldBe 1

        val melding = testRapid.inspektør.message(0)
        melding["@event_name"].asString() shouldBe "meldekortoppretting"
        melding["personId"].asLong() shouldBe personId
        melding["ident"].asString() shouldBe ident
        melding["fraOgMed"].asLocalDateTime() shouldBe fraOgMed
        melding["tilOgMed"].asLocalDateTime() shouldBe tilOgMed
        melding["harRett"].asBoolean() shouldBe false
        melding["handling"].asString() shouldBe "STOPP"
        melding["referanseId"].asString() shouldNotBe null
        melding["skalMigreres"].asBoolean() shouldBe false

        verify(exactly = 1) {
            meldingerRepository.lagreUtgåendeMelding(
                any(),
                ident,
                melding.toString(),
            )
        }
    }
}
