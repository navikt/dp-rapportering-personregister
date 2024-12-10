import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.kafka.consumer.KafkaMessageHandler

private val logger = KotlinLogging.logger {}

class KafkaConsumerRunner(
    private val kafkaConsumerFactory: KafkaConsumerFactory,
    private val listener: KafkaMessageHandler,
) {
    private val kafkaConsumer by lazy {
        kafkaConsumerFactory.createConsumer(listener.topic).apply {
            register(listener)
        }
    }

    fun start() {
        logger.info { "Starter Kafka-consumer for topic: ${listener.topic}" }
        try {
            kafkaConsumer.start()
        } catch (e: Exception) {
            logger.error(e) { "En feil oppstod i Kafka-consumer for topic: ${listener.topic}" }
            stop()
        }
    }

    fun stop() {
        logger.info { "Stopper Kafka-consumer for topic: ${listener.topic}" }
        runCatching { kafkaConsumer.stop() }
            .onFailure { logger.error(it) { "En feil oppstod under lukking av Kafka-consumer for topic: ${listener.topic}" } }
    }
}
