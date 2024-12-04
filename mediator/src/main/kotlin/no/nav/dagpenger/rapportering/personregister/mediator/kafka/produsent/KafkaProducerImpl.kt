package no.nav.dagpenger.rapportering.personregister.mediator.kafka.produsent

import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

class KafkaProducerImpl<T>(
    private val kafkaProducer: KafkaProducer<String, String>,
     val topic: String,
) : KafkaProdusent<T>() {

    override fun send(key: String, value: T) {
        val record = ProducerRecord(topic, key, serialize(value))
        kafkaProducer.send(record) { metadata, exception ->
            if (exception != null) {
                println("Failed to send message: Key=$key, Value=$value, Error=${exception.message}")
            } else {
                println("Message sent successfully: Key=$key, Value=$value to Topic=${metadata.topic()} at Offset=${metadata.offset()}")
            }
        }
    }

    override fun close() {
        kafkaProducer.close()
    }
}
