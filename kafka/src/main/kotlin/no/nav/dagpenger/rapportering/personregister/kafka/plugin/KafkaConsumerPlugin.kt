@file:Suppress("ktlint:standard:filename")

package no.nav.dagpenger.rapportering.personregister.kafka.plugin

import io.ktor.server.application.ApplicationPlugin
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStopping
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.hooks.MonitoringEvent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.kafka.utils.NoopConsumerRebalanceListener
import no.nav.dagpenger.rapportering.personregister.kafka.utils.defaultErrorFunction
import no.nav.dagpenger.rapportering.personregister.kafka.utils.defaultSuccessFunction
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

private const val PLUGIN_NAME_SUFFIX = "KafkaConsumerPlugin"
private val logger = KotlinLogging.logger {}

class KafkaConsumerPluginConfig<K, V> {
    var consumeFunction: ((ConsumerRecords<K, V>) -> Unit)? = null
    var successFunction: ((ConsumerRecords<K, V>) -> Unit)? = null
    var errorFunction: ((throwable: Throwable) -> Unit)? = null
    var kafkaConsumer: KafkaConsumer<K, V>? = null
    var kafkaTopics: Collection<String>? = null
    var pollTimeout: Duration? = null
    var closeTimeout: Duration? = null
    var rebalanceListener: ConsumerRebalanceListener? = null
    val coroutineDispatcher: CoroutineDispatcher? = null
    val shutdownFlag: AtomicBoolean? = null
}

@Suppress("FunctionName")
fun <K, V> KafkaConsumerPlugin(pluginInstance: Any): ApplicationPlugin<KafkaConsumerPluginConfig<K, V>> {
    val pluginName = "${pluginInstance}${PLUGIN_NAME_SUFFIX}"
    return createApplicationPlugin(pluginName, ::KafkaConsumerPluginConfig) {
        logger.info("Installerer {}", pluginName)
        val kafkaTopics = requireNotNull(pluginConfig.kafkaTopics) { "KafkaTopics er null" }
        val kafkaConsumer = requireNotNull(pluginConfig.kafkaConsumer) { "KafkaConsumer er null" }
        val consumeFunction = requireNotNull(pluginConfig.consumeFunction) { "ConsumeFunction er null" }
        val successFunction = pluginConfig.successFunction ?: kafkaConsumer::defaultSuccessFunction
        val errorFunction = pluginConfig.errorFunction ?: ::defaultErrorFunction
        val pollTimeout = pluginConfig.pollTimeout ?: Duration.ofMillis(100)
        val closeTimeout = pluginConfig.closeTimeout ?: Duration.ofSeconds(1)
        val rebalanceListener = pluginConfig.rebalanceListener ?: NoopConsumerRebalanceListener()
        val coroutineDispatcher = pluginConfig.coroutineDispatcher ?: Dispatchers.IO
        val shutdownFlag = pluginConfig.shutdownFlag ?: AtomicBoolean(false)
        var consumeJob: Job? = null

        on(MonitoringEvent(ApplicationStarted)) { application ->
            logger.info("Klargjør {} Kafka Consumer", pluginInstance)
            kafkaConsumer.subscribe(kafkaTopics, rebalanceListener)

            consumeJob =
                application.launch(coroutineDispatcher) {
                    logger.info("Starter {} Kafka Consumer", pluginInstance)
                    while (!shutdownFlag.get()) {
                        try {
                            val records = kafkaConsumer.poll(pollTimeout)
                            consumeFunction(records)
                            successFunction(records)
                        } catch (throwable: Throwable) {
                            kafkaConsumer.unsubscribe()
                            kafkaConsumer.close(closeTimeout)
                            shutdownFlag.set(true)
                            errorFunction(throwable)
                            // TODO: Sette isAlive/isReady til unhealthy
                        }
                    }
                    logger.info("Stoppet {} Kafka Consumer", pluginInstance)
                    consumeJob?.cancel()
                }
        }

        on(MonitoringEvent(ApplicationStopping)) { _ ->
            logger.info("Stopper {} Kafka Consumer", pluginInstance)
            shutdownFlag.set(true)
            consumeJob?.cancel()
            kafkaConsumer.unsubscribe()
            kafkaConsumer.close(closeTimeout)
        }
    }
}
