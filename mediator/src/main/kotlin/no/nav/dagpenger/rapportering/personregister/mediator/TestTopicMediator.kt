package no.nav.dagpenger.rapportering.personregister.mediator

import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.hendelser.TestTopicHendelse

private val logger = KotlinLogging.logger {}

class TestTopicMediator {
    fun behandle(hendelse: TestTopicHendelse) {
        logger.info { "Processerer test topic: $hendelse" }
    }
}
