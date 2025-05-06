package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import io.opentelemetry.instrumentation.annotations.WithSpan
import mu.KotlinLogging
import no.nav.person.pdl.aktor.v2.Aktor
import org.apache.kafka.clients.consumer.ConsumerRecords

class IdentitetshendelserMottak {
    @WithSpan
    fun consume(record: ConsumerRecords<String, Aktor>) =
        record.forEach {
            logger.info("Behandler identitetshendelse med key: ${it.key()}")
            logger.info("Value ${it.value().identifikatorer}")
        }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
