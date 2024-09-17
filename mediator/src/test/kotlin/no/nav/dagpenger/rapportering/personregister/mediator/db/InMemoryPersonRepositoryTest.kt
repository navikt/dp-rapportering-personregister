package no.nav.dagpenger.rapportering.personregister.mediator.db

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.ktor.server.plugins.NotFoundException
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.Status
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class InMemoryPersonRepositoryTest {
    private lateinit var personRepository: PersonRepository
    private lateinit var person: Person

    @BeforeEach
    fun setUp() {
        personRepository = InMemoryPersonRepository()
        person = Person("12345678901", Status.Søkt)
    }

    @Test
    fun `kan lagre en person`() {
        personRepository.lagre(person)
        personRepository.finn(person.ident) shouldBe person
    }

    @Test
    fun `kan ikke finne en person som ikke er lagret`() {
        shouldThrow<NotFoundException> {
            personRepository.finn(person.ident)
        }
    }
}
