package no.nav.dagpenger.rapportering.personregister.mediator

import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.ActionTimer
import no.nav.dagpenger.rapportering.personregister.modell.Hendelse
import no.nav.dagpenger.rapportering.personregister.modell.Person

class FremtidigHendelseMediator(
    private val personRepository: PersonRepository,
    private val actionTimer: ActionTimer,
) {
    fun behandle(hendelse: Hendelse) =
        actionTimer.timedAction("behandle_FremtidigHendelse") {
            sikkerlogg.info { "Mottok fremtidig hendelse: $hendelse" }
            sjekkOmPersonFinnesEllerOpprett(hendelse.ident)
            personRepository.lagreFremtidigHendelse(hendelse)
        }

    private fun sjekkOmPersonFinnesEllerOpprett(ident: String) {
        if (!personRepository.finnesPerson(ident)) {
            personRepository.lagrePerson(Person(ident))
        }
    }

    companion object {
        val sikkerlogg = KotlinLogging.logger("tjenestekall")
    }
}
