package no.nav.dagpenger.rapportering.personregister.mediator.kafka

import com.github.navikt.tbd_libs.kafka.AivenConfig
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClientConfig
import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig
import io.confluent.kafka.serializers.KafkaAvroSerializerConfig
import io.confluent.kafka.serializers.subject.TopicNameStrategy
import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.LongDeserializer
import org.apache.kafka.common.serialization.Serializer
import java.util.Properties
import kotlin.reflect.KClass

private val logger = KotlinLogging.logger {}

object KafkaConfig {
    init {
        // Avro 1.12+ krever eksplisitt tillit til klasser som skal deserialiseres til SpecificRecord.
        // Periode-skjemaet inneholder flere genererte klasser (Metadata, BrukerType, ...) i denne pakken,
        // så vi stoler på hele pakken i stedet for å måtte liste opp hver enkelt genererte klasse.
        System.setProperty("org.apache.avro.SERIALIZABLE_PACKAGES", "no.nav.paw.arbeidssokerregisteret.api.v1")
    }

    private val config = AivenConfig.default

    private val defaultConsumerProperties =
        Properties().apply {
            this[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
            this[KafkaAvroSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG] = System.getenv("KAFKA_SCHEMA_REGISTRY")
            this[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = LongDeserializer::class.java
            this[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = KafkaAvroDeserializer::class.java
            this[KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG] = true
            this[SchemaRegistryClientConfig.BASIC_AUTH_CREDENTIALS_SOURCE] = "USER_INFO"
            this[SchemaRegistryClientConfig.USER_INFO_CONFIG] =
                System.getenv("KAFKA_SCHEMA_REGISTRY_USER") + ":" + System.getenv("KAFKA_SCHEMA_REGISTRY_PASSWORD")
        }

    private val defaultProducerProperties =
        Properties().apply {
            this[KafkaAvroSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG] = System.getenv("KAFKA_SCHEMA_REGISTRY")
            this[KafkaAvroSerializerConfig.VALUE_SUBJECT_NAME_STRATEGY] = TopicNameStrategy::class.java.name
            this[KafkaAvroSerializerConfig.AUTO_REGISTER_SCHEMAS] = true
            this[KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG] = true
            this[SchemaRegistryClientConfig.BASIC_AUTH_CREDENTIALS_SOURCE] = "USER_INFO"
            this[SchemaRegistryClientConfig.USER_INFO_CONFIG] =
                System.getenv("KAFKA_SCHEMA_REGISTRY_USER") + ":" + System.getenv("KAFKA_SCHEMA_REGISTRY_PASSWORD")
        }

    internal fun <T> consumer(
        topicName: String,
        groupId: String,
    ): KafkaConsumer<Long, T> =
        KafkaConsumer<Long, T>(config.consumerConfig(groupId, defaultConsumerProperties)).also {
            logger.info { "Abonnerer på topicName=$topicName med groupId=$groupId" }
            it.subscribe(listOf(topicName))
        }

    internal fun <K : Any, V : Any> producer(
        clientId: String,
        keySerializer: KClass<out Serializer<K>>,
        valueSerializer: KClass<out Serializer<V>>,
    ): Producer<K, V> =
        KafkaProducer(
            config.producerConfig(
                Properties().apply {
                    putAll(defaultProducerProperties)
                    put(ProducerConfig.CLIENT_ID_CONFIG, clientId)
                    put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, keySerializer.java)
                    put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, valueSerializer.java)
                },
            ),
        )
}
