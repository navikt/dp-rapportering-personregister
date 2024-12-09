import com.github.navikt.tbd_libs.kafka.Config
import com.github.navikt.tbd_libs.kafka.ConsumerProducerFactory
import no.nav.dagpenger.rapportering.personregister.mediator.Configuration.APP_NAME
import no.nav.dagpenger.rapportering.personregister.mediator.kafka.konsument.KafkaMessageConsumer

class KafkaConsumerFactory(
    private val kafkaConfig: Config,
) {
    fun createConsumer(topic: String): KafkaMessageConsumer {
        val kafkaConsumer = ConsumerProducerFactory(kafkaConfig).createConsumer(APP_NAME)
        return KafkaMessageConsumer(kafkaConsumer, topic)
    }
}
