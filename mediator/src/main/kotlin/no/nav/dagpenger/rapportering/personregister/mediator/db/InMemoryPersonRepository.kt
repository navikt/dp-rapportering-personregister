package no.nav.dagpenger.rapportering.personregister.mediator.db

import no.nav.dagpenger.rapportering.personregister.modell.Person

class InMemoryPersonRepository : PersonRepository {
    private val personList = mutableMapOf<String, Person>()

    override fun finn(ident: String): Person? = personList[ident]

    override fun lagre(person: Person) {
        personList[person.ident] = person
    }

    override fun oppdater(person: Person) {
        personList[person.ident] = person
    }
}
