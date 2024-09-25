package no.nav.dagpenger.rapportering.personregister.mediator

import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.hendelser.SøknadHendelse
import no.nav.dagpenger.rapportering.personregister.mediator.hendelser.VedtakHendelse
import no.nav.dagpenger.rapportering.personregister.mediator.hendelser.tilHendelse
import no.nav.dagpenger.rapportering.personregister.modell.Hendelse
import no.nav.dagpenger.rapportering.personregister.modell.Person
import java.util.UUID

class PersonstatusMediator(
    private val personRepository: PersonRepository,
) {
    fun behandle(søknadHendelse: SøknadHendelse) {
        behandleHendelse(søknadHendelse.tilHendelse())
    }

    fun behandle(vedtakHendelse: VedtakHendelse) {
        val hendelse =
            Hendelse(
                id = UUID.randomUUID(),
                ident = vedtakHendelse.ident,
                referanseId = vedtakHendelse.referanseId,
                dato = vedtakHendelse.dato,
                status = vedtakHendelse.status,
                kilde = vedtakHendelse.kildesystem,
            )

        behandleHendelse(hendelse)
    }

    private fun behandleHendelse(hendelse: Hendelse) {
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
}
