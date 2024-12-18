package no.nav.dagpenger.rapportering.personregister.mediator.kafka.consumer

import org.apache.kafka.clients.consumer.ConsumerRecord

interface KafkaMessageHandler {
    val topic: String

    fun onMessage(record: ConsumerRecord<String, String>)
}
