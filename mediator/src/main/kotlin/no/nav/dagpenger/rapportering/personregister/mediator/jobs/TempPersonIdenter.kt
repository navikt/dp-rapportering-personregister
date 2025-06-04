package no.nav.dagpenger.rapportering.personregister.mediator.jobs

import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.db.TempPerson
import no.nav.dagpenger.rapportering.personregister.mediator.db.TempPersonRepository

private val logger = KotlinLogging.logger {}

fun hentTempPersonIdenter(tempPersonRepository: TempPersonRepository): List<String> {
    try {
        logger.info { "Henter identer fra persontabell" }
        if (tempPersonRepository.isEmpty()) {
            logger.info { "Fyller midlertidig person tabell" }
            tempPersonRepository.syncPersoner()

            logger.info { "Henter midlertidig identer" }
            val identer = tempPersonRepository.hentAlleIdenter()
            logger.info { "Hentet ${identer.size} midlertidige identer" }

            try {
                identer.map { ident ->
                    try {
                        val tempPerson = TempPerson(ident)
                        logger.info { "Lagrer midlertidig person med ident: ${tempPerson.ident} og status: ${tempPerson.status}" }
                        tempPersonRepository.lagrePerson(tempPerson)
                        logger.info { "Lagring av midlertidig person med ident: ${tempPerson.ident} fullført" }
                    } catch (e: Exception) {
                        logger.error(e) { "Feil ved lagring av midlertidig person med ident: $ident" }
                    }
                }
                logger.info { "Midlertidig person tabell er fylt med ${identer.size}" }

                return tempPersonRepository.hentAlleIdenter()
            } catch (e: Exception) {
                logger.error(e) { "Feil ved lagring av midlertidige personer i databasen" }
            }
        }

        logger.info { "Midlertidig person tabell er allerede fylt." }
        return tempPersonRepository.hentAlleIdenter()
    } catch (e: Exception) {
        logger.error(e) { "Feil ved henting av midlertidig identer" }
        return emptyList()
    }
}
