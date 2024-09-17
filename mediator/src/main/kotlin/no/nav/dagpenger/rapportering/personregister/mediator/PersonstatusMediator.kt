package no.nav.dagpenger.rapportering.personregister.mediator

import no.nav.dagpenger.rapportering.personregister.mediator.db.HendelseRepository
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
    private val hendelseRepository: HendelseRepository,
) {
    fun behandle(
        ident: String,
        soknadId: String,
    ) {
        personRepository
            .finn(ident)
            ?.let { person ->
                personRepository.oppdater(person.copy(status = Søkt))
            } ?: run {
            personRepository.lagre(Person(ident, Søkt))
        }

        hendelseRepository.opprettHendelse(
            Hendelse(
                ident = ident,
                referanseId = soknadId,
                beskrivelse = Søkt.name,
                kilde = DpSoknad,
                mottatt = LocalDateTime.now(),
            ),
        )
    }
}
