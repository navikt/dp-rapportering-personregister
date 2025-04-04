@file:Suppress("ktlint:standard:filename")

package no.nav.dagpenger.rapportering.personregister.kafka.plugin

import io.ktor.server.application.ApplicationPlugin
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStopping
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.hooks.MonitoringEvent
import mu.KotlinLogging
import org.apache.kafka.streams.KafkaStreams
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

private const val PLUGIN_NAME_SUFFIX = "KafkaStreamsPlugin"
private val logger = KotlinLogging.logger {}

/**
 * Configuration class for the KafkaStreamsPlugin.
 * It allows configuring the Kafka Streams properties, as well as handlers for different message types.
 */
class KafkaStreamsPluginConfig {
    var streams: KafkaStreams? = null
    var closeTimeout: Duration? = null
}

/**
 * A Ktor plugin that initializes and starts Kafka Streams.
 * It joins two Kafka topics based on FODSELSNR and processes the messages accordingly.
 */
@Suppress("FunctionName")
fun KafkaStreamsPlugin(pluginInstance: Any): ApplicationPlugin<KafkaStreamsPluginConfig> {
    val pluginName = "${pluginInstance}${PLUGIN_NAME_SUFFIX}"
    return createApplicationPlugin(pluginName, ::KafkaStreamsPluginConfig) {
        logger.info("Installing {}", pluginName)
        val streams = requireNotNull(pluginConfig.streams) { "KafkaStreams is null" }
        val closeTimeout = pluginConfig.closeTimeout ?: Duration.ofSeconds(10)
        val isRunning = AtomicBoolean(false)

        on(MonitoringEvent(ApplicationStarted)) { _ ->
            logger.info("Starting {} Kafka Streams", pluginInstance)
            streams.start()
            isRunning.set(true)
        }

        on(MonitoringEvent(ApplicationStopping)) { _ ->
            logger.info("Stopping {} Kafka Streams", pluginInstance)
            if (isRunning.get()) {
                streams.close(closeTimeout)
                isRunning.set(false)
            }
        }
    }
}
