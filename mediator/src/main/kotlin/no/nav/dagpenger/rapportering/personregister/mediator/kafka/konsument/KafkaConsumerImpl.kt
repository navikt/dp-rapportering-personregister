package no.nav.dagpenger.rapportering.personregister.mediator.kafka.konsument

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import java.time.Duration

open class KafkaConsumerImpl<T>(
    private val kafkaConsumer: KafkaConsumer<String, T>,
    override val topic: String,
    private val handler: (ConsumerRecord<String, T>) -> Unit,
) : KafkaKonsument {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1)) // Use single-threaded context

    override fun stream() {
        scope.launch {
            kafkaConsumer.use { consumer ->
                consumer.subscribe(listOf(topic))
                while (isActive) {
                    val records =
                        runCatching {
                            consumer.poll(Duration.ofSeconds(1))
                        }.getOrElse { throwable ->
                            println("Failed to poll records: ${throwable.message}")
                            return@launch
                        }

                    records.forEach { record ->
                        runCatching {
                            handler(record)
                        }.onFailure { e ->
                            println("Failed to process record: ${record.value()}, error: ${e.message}")
                        }
                    }
                }
            }
        }
    }

    fun stop() {
        scope.cancel("Stopping Kafka Consumer for topic $topic")
    }

    override fun close() {
        kafkaConsumer.close()
    }
}
