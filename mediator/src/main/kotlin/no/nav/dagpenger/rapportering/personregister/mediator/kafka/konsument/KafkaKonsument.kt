package no.nav.dagpenger.rapportering.personregister.mediator.kafka.konsument

import org.apache.kafka.clients.consumer.ConsumerRecord

interface KafkaKonsument {
    val topic: String

    fun stream()

    fun  process(record: ConsumerRecord<String, String>)

    fun close()
}
