package no.nav.dagpenger.rapportering.personregister.mediator.kafka

import com.github.navikt.tbd_libs.kafka.AivenConfig
import com.github.navikt.tbd_libs.kafka.ConsumerProducerFactory
import mu.KotlinLogging
import java.time.Duration

class KafkaConsumer {
    val factory = ConsumerProducerFactory(AivenConfig.default)

    fun start() {
        try {
            factory.createConsumer("my-group").use { consumer ->
                consumer.subscribe(listOf("my-topic"))
                logger.info { "Consumer subscribed to topic 'my-topic'" }

                while (true) {
                    val records = consumer.poll(Duration.ofMillis(1000)) // Poll messages every second
                    records.forEach { record ->
                        logger.info {
                            "Received message: Key=${record.key()}, Value=${record.value()}"
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "KafkaConsumer feilet!" }
        }
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
