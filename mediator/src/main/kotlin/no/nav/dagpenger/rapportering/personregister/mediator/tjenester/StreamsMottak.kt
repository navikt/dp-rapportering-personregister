package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.modell.AnnenMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.DagpengerMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.KombinertHendelse
import no.nav.dagpenger.rapportering.personregister.modell.MeldepliktHendelse

private val logger = KotlinLogging.logger {}

class StreamsMottak {
    fun consume(hendelse: KombinertHendelse) {
        logger.info { "Streams: Mottok ny kombinert meldeplikt og meldegruppehendelse" }
    }

    fun consume(hendelse: MeldepliktHendelse) {
        logger.info { "Streams: Mottok ny meldeplikt hendelse" }
    }

    fun consume(hendelse: DagpengerMeldegruppeHendelse) {
        logger.info { "Streams: Mottok ny dagpenger-meldegruppe hendelse" }
    }

    fun consume(hendelse: AnnenMeldegruppeHendelse) {
        logger.info { "Streams: Mottok ny annen-meldegruppe hendelse" }
    }
}
