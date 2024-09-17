package no.nav.dagpenger.rapportering.personregister.mediator.db

import no.nav.dagpenger.rapportering.personregister.modell.Person

interface PersonRepository {
    fun finn(ident: String): Person?

    fun lagre(person: Person)

    fun oppdater(person: Person)
}
