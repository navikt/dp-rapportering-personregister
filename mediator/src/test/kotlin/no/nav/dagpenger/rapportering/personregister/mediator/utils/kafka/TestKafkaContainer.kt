package no.nav.dagpenger.rapportering.personregister.mediator.utils.kafka

import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerializer
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.kafka.PaaVegneAvAvroSerializer
import no.nav.paw.arbeidssokerregisteret.api.v1.Periode
import no.nav.paw.bekreftelse.paavegneav.v1.PaaVegneAv
import org.apache.avro.specific.SpecificRecordBase
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.Producer
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.kafka.ConfluentKafkaContainer
import org.testcontainers.utility.DockerImageName
import java.time.Duration
import kotlin.reflect.KClass

private const val CONFLUENT_VERSION = "7.8.1"
private const val KAFKA_IMAGE: String = "confluentinc/cp-kafka"
private const val KAFKA_ALIAS = "tc-kafka"
private const val KAFKA_PORT: Int = 19092
private const val SCHEMA_REGISTRY_IMAGE: String = "confluentinc/cp-schema-registry"
private const val SCHEMA_REGISTRY_PORT: Int = 8085
private const val SCHEMA_REGISTRY_ALIAS = "schema-registry"

private val logger = KotlinLogging.logger {}

class TestKafkaContainer {
    private val kafkaNetwork: Network = Network.newNetwork()
    private val kafka: ConfluentKafkaContainer = kafkaContainer(kafkaNetwork)
    private val schemaRegistry: GenericContainer<*> = schemaRegistryContainer(kafkaNetwork)

    fun <T : SpecificRecordBase> createProducer(
        type: KClass<T>,
        topicName: String,
    ): Producer<Long, T> {
        if (!kafka.isRunning) {
            logger.info { "Starting Kafka container for type" }
            kafka.start()
            setupKafkaTopic(topicName)
        }
        if (!schemaRegistry.isRunning) {
            logger.info { "Starting Schema Registry container" }
            schemaRegistry.start()
        }

        val serializer =
            when (type) {
                Periode::class -> PeriodeAvroSerializer::class.qualifiedName
                PaaVegneAv::class -> PaaVegneAvAvroSerializer::class.qualifiedName
                else -> throw IllegalArgumentException("Unknown type")
            }

        val producerConfig =
            mapOf(
                "bootstrap.servers" to kafka.bootstrapServers,
                "schema.registry.url" to "http://${schemaRegistry.host}:${schemaRegistry.firstMappedPort}",
                "key.serializer" to "org.apache.kafka.common.serialization.LongSerializer",
                "value.serializer" to serializer,
                "group.id" to "test-group",
            )
        return KafkaProducer(producerConfig)
    }

    fun createConsumer(): KafkaConsumer<Long, Periode> {
        val consumerConfig =
            mapOf(
                "bootstrap.servers" to kafka.bootstrapServers,
                "schema.registry.url" to "http://${schemaRegistry.host}:${schemaRegistry.firstMappedPort}",
                "group.id" to "test-group",
                "key.deserializer" to "org.apache.kafka.common.serialization.LongDeserializer",
                "value.deserializer" to "no.nav.dagpenger.rapportering.personregister.kafka.PeriodeAvroDeserializer",
                "auto.offset.reset" to "earliest",
            )
        return KafkaConsumer(consumerConfig)
    }

    fun stop() {
        logger.info { "Stopping containers" }
        schemaRegistry.stop()
        kafka.stop()
    }

    private fun setupKafkaTopic(topicName: String) {
        val adminClient = AdminClient.create(mapOf("bootstrap.servers" to kafka.bootstrapServers))
        val newTopic = NewTopic(topicName, 1, 1) // 1 partition, replication factor of 1
        adminClient.createTopics(listOf(newTopic)).all().get() // Blocking until the topic is created
    }
}

fun kafkaContainer(network: Network): ConfluentKafkaContainer =
    ConfluentKafkaContainer(DockerImageName.parse("$KAFKA_IMAGE:$CONFLUENT_VERSION"))
        .withListener("$KAFKA_ALIAS:$KAFKA_PORT")
        .withNetwork(network)
        .withNetworkAliases(KAFKA_ALIAS)
        .withReuse(true)

fun schemaRegistryContainer(network: Network): GenericContainer<*> =
    GenericContainer(DockerImageName.parse(SCHEMA_REGISTRY_IMAGE).withTag(CONFLUENT_VERSION))
        .withExposedPorts(SCHEMA_REGISTRY_PORT)
        .withNetworkAliases(SCHEMA_REGISTRY_ALIAS)
        ?.withNetwork(network)
        ?.withEnv("SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS", "PLAINTEXT://$KAFKA_ALIAS:$KAFKA_PORT")
        ?.withEnv("SCHEMA_REGISTRY_LISTENERS", "http://0.0.0.0:$SCHEMA_REGISTRY_PORT")
        ?.withEnv("SCHEMA_REGISTRY_HOST_NAME", SCHEMA_REGISTRY_ALIAS)
        ?.withEnv("SCHEMA_REGISTRY_KAFKASTORE_SECURITY_PROTOCOL", "PLAINTEXT")
        ?.waitingFor(Wait.forHttp("/subjects").forStatusCode(200))
        ?.withStartupTimeout(Duration.ofSeconds(60))
        ?: throw IllegalStateException("Could not create Schema Registry container")

class PeriodeAvroSerializer : SpecificAvroSerializer<Periode>()
