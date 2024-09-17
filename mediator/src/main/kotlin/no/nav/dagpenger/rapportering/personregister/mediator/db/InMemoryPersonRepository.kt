package no.nav.dagpenger.rapportering.personregister.mediator.db

import io.ktor.server.plugins.NotFoundException
import no.nav.dagpenger.rapportering.personregister.modell.Person

class InMemoryPersonRepository : PersonRepository {
    private val personList = mutableMapOf<String, Person>()

    override fun finn(ident: String): Person = personList[ident] ?: throw NotFoundException("Person med $ident finnes ikke")

    override fun lagre(person: Person): Boolean {
        personList[person.ident] = person
        return true
    }
}
