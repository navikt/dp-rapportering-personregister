package no.nav.dagpenger.rapportering.personregister.mediator.kafka

import com.github.navikt.tbd_libs.kafka.AivenConfig
import com.github.navikt.tbd_libs.kafka.ConsumerProducerFactory
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.Configuration.APP_NAME
import java.time.Duration

private val logger = KotlinLogging.logger {}

class KafkaConsumer {
    private val factory = ConsumerProducerFactory(AivenConfig.default)

    fun start() {
        try {
            logger.info { "Starter kafkaConsumer" }
            factory.createConsumer(APP_NAME).use { consumer ->
                consumer.subscribe(listOf("teamdagpenger.test-topic"))
                logger.info { "Consumer subscribed to topic 'teamdagpenger.test-topic'" }

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
}
