package no.nav.dagpenger.rapportering.personregister.mediator.utils.kafka

import no.nav.paw.bekreftelse.paavegneav.v1.PaaVegneAv
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.common.serialization.Deserializer
import org.testcontainers.kafka.ConfluentKafkaContainer
import org.testcontainers.utility.DockerImageName
import kotlin.reflect.KClass

class TestKafkaContainer {
    private val kafkaContainer: ConfluentKafkaContainer = ConfluentKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"))

    init {
        kafkaContainer.start()
    }

    fun createProducer(): Producer<Long, PaaVegneAv> {
        val producerConfig =
            mapOf(
                "bootstrap.servers" to kafkaContainer.bootstrapServers,
                "key.serializer" to "org.apache.kafka.common.serialization.LongSerializer",
                "value.serializer" to "no.nav.dagpenger.rapportering.personregister.kafka.PaaVegneAvAvroSerializer",
                "schema.registry.url" to "http://${kafkaContainer.host}:${kafkaContainer.firstMappedPort}",
            )
        return KafkaProducer(producerConfig)
    }

    fun <K, V> createConsumer(
        groupId: String = "test-group",
        keyDeserializer: KClass<out Deserializer<K>>,
        valueDeserializer: KClass<out Deserializer<V>>,
    ): KafkaConsumer<K, V> {
        val consumerConfig =
            mapOf(
                "bootstrap.servers" to kafkaContainer.bootstrapServers,
                "schema.registry.url" to "http://${kafkaContainer.host}:${kafkaContainer.firstMappedPort}",
                "group.id" to groupId,
                "key.deserializer" to keyDeserializer.java.name,
                "value.deserializer" to valueDeserializer.java.name,
                "auto.offset.reset" to "earliest",
            )
        return KafkaConsumer(consumerConfig)
    }

    fun stop() {
        kafkaContainer.stop()
    }
}
