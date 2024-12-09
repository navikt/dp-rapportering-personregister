import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.kafka.konsument.KafkaMessageHandler
import java.util.concurrent.atomic.AtomicBoolean

private val logger = KotlinLogging.logger {}

class KafkaConsumerRunner(
    private val kafkaConsumerFactory: KafkaConsumerFactory,
    private val listener: KafkaMessageHandler,
) {
    private val isRunning = AtomicBoolean(false)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val kafkaKonsument by lazy {
        kafkaConsumerFactory.createConsumer(listener.topic)
    }

    fun start() {
        if (isRunning.compareAndSet(false, true)) {
            logger.info { "Starter Kafka-consumer for topic: ${listener.topic}" }
            scope.launch {
                try {
                    kafkaKonsument.start()
                } catch (e: CancellationException) {
                    logger.info { "Kafka-consumer for topic ${listener.topic} ble avbrutt" }
                } catch (e: Exception) {
                    logger.error(e) { "En feil oppstod i Kafka-consumer for topic: ${listener.topic}" }
                } finally {
                    stop()
                }
            }
        } else {
            logger.warn { "Kafka-consumer for topic ${listener.topic} kjører allerede og kan ikke startes på nytt" }
        }
    }

    fun stop() {
        if (isRunning.compareAndSet(true, false)) {
            logger.info { "Stopper Kafka-consumer for topic: ${listener.topic}" }
            scope.cancel("Kafka-consumer stoppet")
            runCatching { kafkaKonsument.stop() }
                .onFailure { logger.error(it) { "En feil oppstod under lukking av Kafka-consumer for topic: ${listener.topic}" } }
        } else {
            logger.warn { "Kafka-consumer for topic ${listener.topic} er allerede stoppet" }
        }
    }
}
