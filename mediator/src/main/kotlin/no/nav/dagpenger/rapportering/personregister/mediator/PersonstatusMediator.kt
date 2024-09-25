package no.nav.dagpenger.rapportering.personregister.mediator

import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.hendelser.SøknadHendelse
import no.nav.dagpenger.rapportering.personregister.mediator.hendelser.VedtakHendelse
import no.nav.dagpenger.rapportering.personregister.mediator.hendelser.tilHendelse
import no.nav.dagpenger.rapportering.personregister.modell.Person

class PersonstatusMediator(
    private val personRepository: PersonRepository,
) {
    fun behandle(søknadHendelse: SøknadHendelse) {
        val hendelse = søknadHendelse.tilHendelse()

        personRepository
            .hentPerson(hendelse.ident)
            ?.let { person ->
                person.behandle(hendelse)
                personRepository.oppdaterPerson(person)
            } ?: run {
            Person(hendelse.ident).apply {
                behandle(hendelse)
                personRepository.lagrePerson(this)
            }
        }
    }

    fun behandle(vedtakHendelse: VedtakHendelse) {
    }
}
