package no.nav.dagpenger.rapportering.personregister.kafka.utils

import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.TopicPartition

private val logger = KotlinLogging.logger {}

fun <K, V> KafkaConsumer<K, V>.defaultSuccessFunction(records: ConsumerRecords<K, V>) {
    if (!records.isEmpty) {
        logger.debug { "Kafka Consumer success. ${records.count()} records processed" }
        this.commitSync()
    }
}

class NoopConsumerRebalanceListener : ConsumerRebalanceListener {
    override fun onPartitionsRevoked(partitions: MutableCollection<TopicPartition>?) {}

    override fun onPartitionsAssigned(partitions: MutableCollection<TopicPartition>?) {}
}
