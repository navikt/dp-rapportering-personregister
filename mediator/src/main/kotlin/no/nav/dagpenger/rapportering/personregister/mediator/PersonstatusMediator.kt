package no.nav.dagpenger.rapportering.personregister.mediator

import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.modell.Hendelse
import no.nav.dagpenger.rapportering.personregister.modell.Kildesystem.DpSoknad
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.Status.Søkt
import no.nav.helse.rapids_rivers.RapidsConnection
import java.time.LocalDateTime

class PersonstatusMediator(
    private val rapidsConnection: RapidsConnection,
    private val personRepository: PersonRepository,
) {
    fun behandle(
        ident: String,
        soknadId: String,
    ) {
        val hendelse =
            Hendelse(personId = ident, referanseId = soknadId, beskrivelse = Søkt.name, kilde = DpSoknad, mottatt = LocalDateTime.now())

        personRepository
            .finn(ident)
            .let { person ->
                if (person == null) {
                    personRepository.lagre(Person(ident, Søkt))
                } else {
                    personRepository.oppdater(person.copy(status = Søkt))
                }
            }

        // Sjekke om personen finnes i databasen
        // Hvis personen finnes, oppdater status hvis nødvendig og lagre hendelse
        // Hvis personen ikke finnes, lagre person og hendelse
        // Rest-endepunk for å gi personstatus til dp-rapportering

        // V2:
        // Legge melding på kafka om at meldekort må lages for person i inneværende periode
        // Regelmessig sende melding på kafka om at meldekort må påfylles for bruker
    }
}
