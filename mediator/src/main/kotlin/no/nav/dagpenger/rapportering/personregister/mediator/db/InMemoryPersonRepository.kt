package no.nav.dagpenger.rapportering.personregister.mediator.db

import no.nav.dagpenger.rapportering.personregister.modell.Person

class InMemoryPersonRepository : PersonRepository {
    private val personList = mutableMapOf<String, Person>()

    override fun hentPerson(ident: String): Person? = personList[ident]

    override fun lagrePerson(person: Person) {
        personList[person.ident] = person
    }

    override fun oppdaterPerson(person: Person) {
        personList[person.ident] = person
    }
}
