package no.nav.dagpenger.rapportering.personregister.mediator.db

import no.nav.dagpenger.rapportering.personregister.modell.Person

interface PersonRepository {
    fun hentPerson(ident: String): Person?

    fun lagrePerson(person: Person)

    fun oppdaterPerson(person: Person)

    fun hentAnallPersoner(): Int

    fun hentAntallHendelser(): Int
}
