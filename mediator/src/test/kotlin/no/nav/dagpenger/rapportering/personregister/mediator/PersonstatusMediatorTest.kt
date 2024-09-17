package no.nav.dagpenger.rapportering.personregister.mediator

import io.kotest.matchers.shouldBe
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.Status
import no.nav.dagpenger.rapportering.personregister.modell.Status.Avslag
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.junit.jupiter.api.Test

class PersonstatusMediatorTest {
    val rapidsConnection = TestRapid()
    val personRepository: PersonRepository = TestPersonRepository()
    val personstatusMediator = PersonstatusMediator(rapidsConnection, personRepository)

    @Test
    fun `kan behandle en ny person`() {
        val ident = "12345678910"
        val soknadId = "123"

        personRepository.finn(ident) shouldBe null

        personstatusMediator.behandle(ident, soknadId)

        personRepository.finn(ident) shouldBe Person(ident, Status.Søkt)
    }

    @Test
    fun `kan behandle eksisterende person`() {
        val ident = "12345678910"
        val soknadId = "123"

        personRepository.lagre(Person(ident, Avslag))

        personstatusMediator.behandle(ident, soknadId)

        personRepository.finn(ident) shouldBe Person(ident, Status.Søkt)
    }
}

class TestPersonRepository : PersonRepository {
    val personliste = mutableMapOf<String, Person>()

    override fun finn(ident: String): Person? = personliste[ident]

    override fun lagre(person: Person) {
        personliste[person.ident] = person
    }

    override fun oppdater(person: Person) {
        personliste.replace(person.ident, person)
    }
}
