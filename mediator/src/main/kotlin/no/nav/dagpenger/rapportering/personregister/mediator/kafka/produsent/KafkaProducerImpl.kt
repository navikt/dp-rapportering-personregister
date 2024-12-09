package no.nav.dagpenger.rapportering.personregister.mediator.kafka.produsent

import mu.KotlinLogging
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

class KafkaProducerImpl<T>(
    private val kafkaProducer: KafkaProducer<String, String>,
    val topic: String,
) : KafkaProdusent<T>() {
    override fun send(
        key: String,
        value: T,
    ) {
        val record = ProducerRecord(topic, key, serialize(value))
        kafkaProducer.send(record) { metadata, exception ->
            if (exception != null) {
                logger.info { "Kunne ikke sende melding: Nøkkel=$key, Verdi=$value, Feil=${exception.message}" }
            } else {
                logger.info { "Melding sendt: Nøkkel=$key, Verdi=$value til Topic=${metadata.topic()} på Offset=${metadata.offset()}" }
            }
        }
    }

    override fun close() {
        kafkaProducer.close()
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
