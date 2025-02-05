package no.nav.dagpenger.rapportering.personregister.kafka

import io.confluent.kafka.serializers.KafkaAvroDeserializer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.Serializer
import java.util.Properties
import kotlin.reflect.KClass

class KafkaFactory(
    private val kafkaServerConfig: KafkaKonfigurasjon,
) {
    val baseProperties =
        Properties().apply {
            putAll(kafkaServerConfig.properties)
        }

    /*fun createConsumer(topic: String): KafkaKonsument {
        val kafkaConsumer = ConsumerProducerFactory(kafkaConfig).createConsumer(APP_NAME)
        return KafkaKonsument(kafkaConsumer, topic)
    }*/

    fun <K : Any, V : Any> createKafkaAvroValueConsumer(
        groupId: String,
        clientId: String,
        keyDeserializer: KClass<out Deserializer<K>>,
        autoCommit: Boolean = false,
        autoOffsetReset: String = "earliest",
        maxPollrecords: Int = ConsumerConfig.DEFAULT_MAX_POLL_RECORDS,
    ): KafkaConsumer<K, V> =
        KafkaConsumer(
            baseProperties +
                mapOf(
                    ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to autoCommit,
                    ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to autoOffsetReset,
                    ConsumerConfig.GROUP_ID_CONFIG to groupId,
                    ConsumerConfig.CLIENT_ID_CONFIG to clientId,
                    ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to keyDeserializer.java,
                    ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to KafkaAvroDeserializer::class.java,
                    ConsumerConfig.MAX_POLL_RECORDS_CONFIG to maxPollrecords,
                ),
        )

    @Suppress("UNCHECKED_CAST")
    fun <T> kafkaAvroDeSerializer(): Deserializer<T> {
        val map: Map<String, Any> = baseProperties.toMap().mapKeys { it.key.toString() }
        val deserializer =
            KafkaAvroDeserializer().apply {
                configure(map, false)
            }
        return deserializer as Deserializer<T>
    }

    fun <K : Any, V : Any> createProducer(
        clientId: String,
        keySerializer: KClass<out Serializer<K>>,
        valueSerializer: KClass<out Serializer<V>>,
        acks: String = "all",
    ): Producer<K, V> =
        KafkaProducer(
            baseProperties +
                mapOf(
                    ProducerConfig.ACKS_CONFIG to acks,
                    ProducerConfig.CLIENT_ID_CONFIG to clientId,
                    ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to keySerializer.java,
                    ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to valueSerializer.java,
                ),
        )

    fun <K : Any, V : Any> createConsumer(
        groupId: String,
        clientId: String,
        keyDeserializer: KClass<out Deserializer<K>>,
        valueDeserializer: KClass<out Deserializer<V>>,
        autoCommit: Boolean = false,
        autoOffsetReset: String = "earliest",
        maxPollrecords: Int = ConsumerConfig.DEFAULT_MAX_POLL_RECORDS,
    ): KafkaConsumer<K, V> =
        KafkaConsumer(
            baseProperties +
                mapOf(
                    ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to autoCommit,
                    ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to autoOffsetReset,
                    ConsumerConfig.GROUP_ID_CONFIG to groupId,
                    ConsumerConfig.CLIENT_ID_CONFIG to clientId,
                    ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to keyDeserializer.java,
                    ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to valueDeserializer.java,
                    ConsumerConfig.MAX_POLL_RECORDS_CONFIG to maxPollrecords,
                ),
        )
}

operator fun Properties.plus(other: Map<String, Any>): Properties =
    Properties().apply {
        putAll(this@plus)
        putAll(other)
    }

fun <K, V> Producer<K, V>.sendDeferred(record: ProducerRecord<K, V>): Deferred<RecordMetadata> {
    val deferred = CompletableDeferred<RecordMetadata>()
    send(record) { metadata, exception ->
        if (exception != null) {
            deferred.completeExceptionally(exception)
        } else {
            deferred.complete(metadata)
        }
    }
    return deferred
}
