package no.nav.dagpenger.rapportering.personregister.mediator.observers

import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDate
import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import no.nav.dagpenger.rapportering.personregister.mediator.ApplicationBuilder
import no.nav.dagpenger.rapportering.personregister.mediator.ApplicationBuilder.Companion.getRapidsConnection
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.modell.Person
import org.junit.jupiter.api.Test
import java.time.LocalDate

class PersonObserverMeldekortregisterTest {
    @Test
    fun `kan sende Start-melding`() {
        val testRapid = TestRapid()
        mockkObject(ApplicationBuilder.Companion)
        every { getRapidsConnection() } returns testRapid

        val personId = 1234L
        val ident = "12345678910"
        val person = Person(ident)

        val personRepository = mockk<PersonRepository>(relaxed = true)
        every { personRepository.hentPersonId(eq(ident)) } returns personId

        val personObserverMeldekortregister = PersonObserverMeldekortregister(personRepository)

        personObserverMeldekortregister.sendStartMeldingTilMeldekortregister(person)

        testRapid.inspektør.size shouldBe 1

        val message = testRapid.inspektør.message(0)
        message["@event_name"].asText() shouldBe "meldekortoppretting"
        message["personId"].asLong() shouldBe personId
        message["ident"].asText() shouldBe ident
        message["dato"].asLocalDate() shouldBe LocalDate.now()
        message["handling"].asText() shouldBe "START"
    }
}
