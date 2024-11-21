package no.nav.dagpenger.rapportering.personregister.mediator.kafka

import mu.KotlinLogging
import org.apache.kafka.clients.consumer.KafkaConsumer

private val logger = KotlinLogging.logger {}

class TestKonsument(
    konsument: KafkaConsumer<String, String>,
    topic: String,
) : KafkaKonsument<String>(consumer = konsument, topic = topic) {
    override fun stream() {
        stream { meldinger ->
            meldinger.forEach { melding ->
                logger.info {
                    "Received message: Key=${melding.key()}, Value=${melding.value()}"
                }
            }
        }
    }
}
