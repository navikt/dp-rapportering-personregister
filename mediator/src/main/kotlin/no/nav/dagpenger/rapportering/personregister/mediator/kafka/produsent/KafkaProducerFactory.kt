package no.nav.dagpenger.rapportering.personregister.mediator.kafka.produsent

import com.github.navikt.tbd_libs.kafka.Config
import com.github.navikt.tbd_libs.kafka.ConsumerProducerFactory

class KafkaProducerFactory(
    private val kafkaConfig: Config,
) {
    fun <T> createProducer(topic: String): KafkaProdusent<T> {
        val kafkaProducer = ConsumerProducerFactory(kafkaConfig).createProducer()

        return KafkaProducerImpl(
            kafkaProducer = kafkaProducer,
            topic = topic,
        )
    }
}
