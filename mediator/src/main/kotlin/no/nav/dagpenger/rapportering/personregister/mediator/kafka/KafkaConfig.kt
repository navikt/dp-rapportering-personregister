package no.nav.dagpenger.rapportering.personregister.mediator.kafka

import com.github.navikt.tbd_libs.kafka.AivenConfig
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClientConfig
import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig
import io.confluent.kafka.serializers.KafkaAvroSerializerConfig
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.LongDeserializer
import java.util.Properties

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

    internal fun <T> consumer(
        topicName: String,
        groupId: String,
    ): KafkaConsumer<Long, T> =
        KafkaConsumer<Long, T>(config.consumerConfig(groupId, defaultConsumerProperties)).also {
            it.subscribe(listOf(topicName))
        }
}
