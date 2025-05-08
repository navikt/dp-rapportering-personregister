package no.nav.dagpenger.rapportering.personregister.mediator.service

import no.nav.dagpenger.pdl.PDLIdentliste
import no.nav.dagpenger.rapportering.personregister.mediator.connector.PdlConnector
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.modell.Person

class PersonService(
    private val pdlConnector: PdlConnector,
    private val personRepository: PersonRepository,
) {
    private fun ryddOppPersoner(
        pdlIdentliste: PDLIdentliste,
        identer: List<String>,
    ) {
        TODO()
    }

    private fun hentPersonFraDB(identer: List<String>): List<Person> = identer.mapNotNull { ident -> personRepository.hentPerson(ident) }

    private fun hentAlleIdenterForPerson(ident: String): PDLIdentliste = pdlConnector.hentIdenter(ident)
}
