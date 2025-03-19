package no.nav.dagpenger.rapportering.personregister.mediator

import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.ActionTimer
import no.nav.dagpenger.rapportering.personregister.modell.Hendelse

class FremtidigHendelseMediator(
    private val personRepository: PersonRepository,
    private val actionTimer: ActionTimer,
) {
    fun behandle(hendelse: Hendelse) =
        actionTimer.timedAction("behandle_FremtidigHendelse") {
            sikkerlogg.info { "Mottok fremtidig hendelse: $hendelse" }
            personRepository.lagreFremtidigHendelse(hendelse)
        }

    companion object {
        val sikkerlogg = KotlinLogging.logger("tjenestekall")
    }
}
