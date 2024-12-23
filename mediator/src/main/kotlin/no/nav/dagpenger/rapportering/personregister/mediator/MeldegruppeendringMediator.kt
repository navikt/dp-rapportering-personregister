package no.nav.dagpenger.rapportering.personregister.mediator

import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.hendelser.MeldegruppeendringHendelse

class MeldegruppeendringMediator() {
    fun behandle(meldegruppeendringHendelse: MeldegruppeendringHendelse) {
        sikkerlogg.info { "Behandler meldegruppeEndretHendelse: $meldegruppeendringHendelse" }
    }

    companion object {
        val sikkerlogg = KotlinLogging.logger("tjenestekall")
    }
}
