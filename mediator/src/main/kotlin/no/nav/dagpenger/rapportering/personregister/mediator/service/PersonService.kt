package no.nav.dagpenger.rapportering.personregister.mediator.service

import mu.KotlinLogging
import no.nav.dagpenger.pdl.PDLIdent
import no.nav.dagpenger.pdl.PDLIdentliste
import no.nav.dagpenger.rapportering.personregister.mediator.connector.PdlConnector
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.modell.Person

class PersonService(
    private val pdlConnector: PdlConnector,
    private val personRepository: PersonRepository,
) {
    /*fun hentPerson(ident: String): Person? {
        val pdlIdenter = hentAlleIdenterForPerson(ident)
        val personer = hentPersonFraDB(pdlIdenter.identer.map { it.ident })

        return ryddOppPersoner(pdlIdenter, personer)
    }

    private fun ryddOppPersoner(
        pdlIdentliste: PDLIdentliste,
        personer: List<Person>,
    ): Person? {
        if (personer.isEmpty()) {
            return null
        } else if (personer.size == 1) {
            val person = personer.first()
            val gjeldendeIdent = pdlIdentliste.identer.hentGjeldendeIdent()
            if (gjeldendeIdent != null && person.ident != gjeldendeIdent) {
                personRepository.oppdaterIdent(person, gjeldendeIdent)
                logger.info("Oppdaterer person med personId ${person.id}")
                return person.copy(ident = gjeldendeIdent)
            } else {
                return person
            }
        } else {
            val gjeldendeIdent = pdlIdentliste.identer.hentGjeldendeIdent()
            val person = personer.firstOrNull { it.ident == gjeldendeIdent }
            if (gjeldendeIdent != null && person != null) {
                if (pdlIdentliste.identer.size > 1) {
                    pdlIdentliste.identer
                        .filter { it.ident != gjeldendeIdent }
                        .forEach { ident ->
                            personRepository.slettPerson(ident)

                            // Overfører arbeidssøkerbekreftelsen til gjeldende person
                        }
                }
                return person
            } else {
                logger.warn("Fant ingen gjeldende ident for ${personer.map { it.ident }}")
                return null
            }
        }
    }


    - Hvis det kun finnes en person i databasen, oppdater med gjeldende ident fra pdl hvis nødvendig
    - Hvis det finnes flere personer i databasen:
     - må vi sjekke om det finnes en som har gjeldende ident i pdl
        - hvis ja, slett de andre personene (eller hvertall pass på at disse ikke er dagpengebrukere)
        - hvis nei, ??
     */

    private fun hentPersonFraDB(identer: List<String>): List<Person> = identer.mapNotNull { ident -> personRepository.hentPerson(ident) }

    private fun hentAlleIdenterForPerson(ident: String): PDLIdentliste = pdlConnector.hentIdenter(ident)

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}

private fun List<PDLIdent>.hentGjeldendeIdent(): String? =
    (
        this.firstOrNull { it.gruppe == PDLIdent.PDLIdentGruppe.FOLKEREGISTERIDENT && !it.historisk }
            ?: this.firstOrNull { it.gruppe == PDLIdent.PDLIdentGruppe.NPID && !it.historisk }
    )?.ident
