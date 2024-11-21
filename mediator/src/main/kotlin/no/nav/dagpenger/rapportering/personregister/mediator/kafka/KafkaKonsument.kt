package no.nav.dagpenger.rapportering.personregister.mediator.kafka

import mu.KotlinLogging
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.errors.WakeupException
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

private val logger = KotlinLogging.logger {}

abstract class KafkaKonsument<T>(
    val consumer: KafkaConsumer<String, T>,
    val topic: String,
    protected val closed: AtomicBoolean = AtomicBoolean(false),
) {
    init {
        Runtime.getRuntime().addShutdownHook(
            Thread {
                closed.set(true)
                consumer.wakeup(); // trådsikker, avbryter konsumer fra polling
            },
        )
    }

    private var antallMeldinger = 0

    fun getAntallMeldinger() = antallMeldinger

    abstract fun stream()

    fun stream(haandter: (ConsumerRecords<String, T>) -> Unit) {
        try {
            logger.info("Starter å lese hendelser fra ${this.javaClass.name}")
            consumer.subscribe(listOf(topic))
            while (!closed.get()) {
                val meldinger: ConsumerRecords<String, T> = consumer.poll(Duration.ofSeconds(10))
                haandter(meldinger)
                consumer.commitSync()

                antallMeldinger += meldinger.count()
                if (meldinger.isEmpty) Thread.sleep(500L)
            }
        } catch (e: WakeupException) {
            // Ignorerer exception hvis vi stenger ned
            if (!closed.get()) throw e
        } finally {
            logger.info("Ferdig med å lese hendelser fra $${this.javaClass.name} - lukker consumer")
            consumer.close()
        }
    }
}
