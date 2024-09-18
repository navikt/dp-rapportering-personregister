package no.nav.dagpenger.rapportering.personregister.mediator

import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.SøknadHendelse
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.tilHendelse
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.helse.rapids_rivers.RapidsConnection

class PersonstatusMediator(
    private val rapidsConnection: RapidsConnection,
    private val personRepository: PersonRepository,
) {
    fun behandle(søknadHendelse: SøknadHendelse) {
        val hendelse = søknadHendelse.tilHendelse()

        personRepository
            .finn(hendelse.ident)
            ?.let { person ->
                person.behandle(hendelse)
                personRepository.oppdater(person)
            } ?: run {
            Person(hendelse.ident).apply {
                behandle(hendelse)
                personRepository.lagre(this)
            }
        }
    }
}
