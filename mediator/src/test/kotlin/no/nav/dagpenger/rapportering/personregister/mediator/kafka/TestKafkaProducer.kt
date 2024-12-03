package no.nav.dagpenger.rapportering.personregister.mediator.kafka

import no.nav.dagpenger.rapportering.personregister.mediator.Configuration.defaultObjectMapper
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

class TestKafkaProducer<T>(
    private val producer: KafkaProducer<String, String>,
    private val topic: String,
) {
    fun send(
        key: String,
        message: T,
    ) {
        val messageString = defaultObjectMapper.writeValueAsString(message)
        producer.send(ProducerRecord(topic, key, messageString))
        producer.flush()
    }

    fun send(
        key: String,
        message: String,
    ) {
        producer.send(ProducerRecord(topic, key, message))
        producer.flush()
    }
}
