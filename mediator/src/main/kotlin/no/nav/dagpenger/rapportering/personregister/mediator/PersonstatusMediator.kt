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
            Hendelse(
                ident = ident,
                dato = LocalDateTime.now(),
                referanseId = soknadId,
                status = Søkt,
                kilde = DpSoknad,
            )
        personRepository
            .finn(ident)
            ?.let { person ->
                println(person)
                person.behandle(hendelse)
                println(person)
//                personRepository.oppdater(person.copy(status = Søkt))
            } ?: run {
            Person(ident).apply {
                behandle(hendelse)
                personRepository.lagre(this)
            }
        }
    }
}
