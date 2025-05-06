package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import io.opentelemetry.instrumentation.annotations.WithSpan
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.Configuration.defaultObjectMapper
import no.nav.person.pdl.aktor.v2.Aktor
import org.apache.kafka.clients.consumer.ConsumerRecords

class IdentitetshendelserMottak {
    @WithSpan
    fun consume(record: ConsumerRecords<Long, Aktor>) =
        record.forEach {
            logger.info("Behandler identitetshendelse med key: ${it.key()}")
            logger.info(defaultObjectMapper.writeValueAsString(it.value()))
        }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
