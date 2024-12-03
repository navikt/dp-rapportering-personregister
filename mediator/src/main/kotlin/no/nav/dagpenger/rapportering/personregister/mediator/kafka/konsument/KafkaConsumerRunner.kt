package no.nav.dagpenger.rapportering.personregister.mediator.kafka.konsument

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import mu.KotlinLogging
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException

private val logger = KotlinLogging.logger {}

class KafkaConsumerRunner(
    private val consumer: KafkaKonsument,
) {
    private val isRunning = AtomicBoolean(false)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun start() {
        if (isRunning.compareAndSet(false, true)) {
            scope.launch {
                try {
                    logger.info { "Starter KafkaRunner for topic: ${consumer.topic}" }
                    consumer.stream()
                } catch (e: CancellationException) {
                    logger.info { "KafkaRunner for topic ${consumer.topic} ble kansellert" }
                } catch (e: Exception) {
                    logger.error(e) { "Feil i KafkaRunner for topic: ${consumer.topic}" }
                } finally {
                    stop()
                }
            }
        } else {
            logger.warn { "KafkaRunner for topic ${consumer.topic} kjører allerede" }
        }
    }

    private fun stop() {
        if (isRunning.compareAndSet(true, false)) {
            logger.info { "Stopper KafkaRunner for topic: ${consumer.topic}" }
            scope.cancel("KafkaRunner stopped")
            consumer.close()
        } else {
            logger.warn { "KafkaRunner for topic ${consumer.topic} kjører ikke" }
        }
    }
}
