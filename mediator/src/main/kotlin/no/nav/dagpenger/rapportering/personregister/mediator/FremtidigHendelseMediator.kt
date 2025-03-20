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
            logger.info { "Mottok fremtidig hendelse: ${hendelse.referanseId}" }
            personRepository.lagreFremtidigHendelse(hendelse)
        }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
