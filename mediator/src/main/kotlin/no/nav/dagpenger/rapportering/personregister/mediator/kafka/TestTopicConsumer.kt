package no.nav.dagpenger.rapportering.personregister.mediator.kafka

import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.Configuration.defaultObjectMapper
import no.nav.dagpenger.rapportering.personregister.mediator.TestTopicMediator
import no.nav.dagpenger.rapportering.personregister.mediator.hendelser.TestTopicHendelse
import no.nav.dagpenger.rapportering.personregister.mediator.kafka.konsument.KafkaConsumerImpl
import org.apache.kafka.clients.consumer.KafkaConsumer

private val logger = KotlinLogging.logger {}

class TestTopicConsumer(
    kafkaConsumer: KafkaConsumer<String, String>,
    topic: String,
    private val mediator: TestTopicMediator,
) : KafkaConsumerImpl<String>(
        kafkaConsumer = kafkaConsumer,
        topic = topic,
        handler = { record ->
            logger.info("Received message: Key=${record.key()}, Value=${record.value().trim()}")
            val hendelse = objectMapper.readValue(record.value().trim(), TestTopicHendelse::class.java)
            mediator.behandle(hendelse)
        },
    ) {
    companion object {
        private val objectMapper = defaultObjectMapper
    }
}
