package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import no.nav.dagpenger.rapportering.personregister.modell.AnnenMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.DagpengerMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.KombinertHendelse
import no.nav.dagpenger.rapportering.personregister.modell.MeldepliktHendelse

class StreamsMottak {
    fun consume(hendelse: KombinertHendelse) {
        TODO()
    }

    fun consume(hendelse: MeldepliktHendelse) {
        TODO()
    }

    fun consume(hendelse: DagpengerMeldegruppeHendelse) {
        TODO()
    }

    fun consume(hendelse: AnnenMeldegruppeHendelse) {
        TODO()
    }
}
