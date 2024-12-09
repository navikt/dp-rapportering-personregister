package no.nav.dagpenger.rapportering.personregister.mediator.kafka.konsument

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import mu.KotlinLogging
import org.apache.kafka.clients.consumer.KafkaConsumer
import java.time.Duration

class KafkaMessageConsumer(
    private val kafkaConsumer: KafkaConsumer<String, String>,
    private val topic: String,
) {
    private val listeners = mutableListOf<KafkaMessageHandler>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun register(listener: KafkaMessageHandler): KafkaMessageConsumer {
        listeners.add(listener)
        return this
    }

    fun start() {
        scope.launch {
            kafkaConsumer.use { consumer ->
                consumer.subscribe(listOf(topic))
                try {
                    while (isActive) {
                        val records = consumer.pollSafely(Duration.ofSeconds(1)) ?: continue
                        records.forEach { record ->
                            listeners.forEach { listener ->
                                runCatching { listener.onMessage(record) }.onFailure {
                                    logger.error {
                                        "Kunne ikke behandle melding: ${record.value()}, feilmelding: ${it.message}"
                                    }
                                }
                            }
                        }
                    }
                } finally {
                    logger.info { "Stopper Kafka-consumer for topic: $topic" }
                }
            }
        }
    }

    fun stop() {
        scope.cancel("Stopper Kafka-consumer for topic: $topic")
    }

    private fun KafkaConsumer<String, String>.pollSafely(duration: Duration) =
        runCatching { poll(duration) }
            .onFailure {
                logger.error { "Kunne ikke konsumere meldinger: ${it.message}" }
            }.getOrNull()

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
