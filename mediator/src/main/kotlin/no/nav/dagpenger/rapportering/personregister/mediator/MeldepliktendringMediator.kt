package no.nav.dagpenger.rapportering.personregister.mediator

import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.hendelser.MeldepliktendringHendelse

class MeldepliktendringMediator() {
    fun behandle(meldepliktendringHendelse: MeldepliktendringHendelse) {
        sikkerlogg.info { "Behandler meldegruppeEndretHendelse: $meldepliktendringHendelse" }
    }

    companion object {
        val sikkerlogg = KotlinLogging.logger("tjenestekall")
    }
}
