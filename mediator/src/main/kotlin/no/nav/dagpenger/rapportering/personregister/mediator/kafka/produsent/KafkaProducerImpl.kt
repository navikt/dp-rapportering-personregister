package no.nav.dagpenger.rapportering.personregister.mediator.kafka.produsent

import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata

class KafkaProducerImpl<T>(
    private val producer: KafkaProducer<String, String>,
    private val topic: String,
) : KafkaProdusent<T>(topic) {
    override fun send(value: T): Pair<Int, Long> {
        val serializedValue = serialize(value)
        val record = ProducerRecord<String, String>(topic, serializedValue)
        val metadata: RecordMetadata = producer.send(record).get()
        return metadata.partition() to metadata.offset()
    }

    override fun close() {
        producer.close()
    }
}
