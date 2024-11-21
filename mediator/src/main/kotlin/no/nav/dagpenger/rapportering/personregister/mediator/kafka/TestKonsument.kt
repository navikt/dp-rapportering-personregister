package no.nav.dagpenger.rapportering.personregister.mediator.kafka

import mu.KotlinLogging
import org.apache.kafka.clients.consumer.KafkaConsumer

private val logger = KotlinLogging.logger {}

class TestKonsument(
    konsument: KafkaConsumer<String, String>,
    topic: String,
) : KafkaKonsument<String>(consumer = konsument, topic = topic) {
    // private val factory = ConsumerProducerFactory(AivenConfig.default)

    override fun stream() {
        stream { meldinger ->
            meldinger.forEach { melding ->
                logger.info {
                    "Received message: Key=${melding.key()}, Value=${melding.value()}"
                }
            }
        }
    }

   /* fun start() {
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
    }*/
}
