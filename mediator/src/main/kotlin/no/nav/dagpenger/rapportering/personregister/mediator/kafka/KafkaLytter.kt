package no.nav.dagpenger.rapportering.personregister.mediator.kafka

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.utils.UUIDv7
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.errors.WakeupException
import java.time.Duration
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class KafkaLytter<T>(
    private val consumer: Consumer<Long, T>,
    private val rapidsConnection: RapidsConnection,
    private val block: (korrelasjonsId: UUID, T) -> Unit,
) : RapidsConnection.StatusListener {
    private val running = AtomicBoolean(true)
    private val consumerThread: Thread

    init {
        consumerThread = Thread { consumeLoop() }.apply { isDaemon = true }
    }

    private companion object {
        val logger = KotlinLogging.logger {}
    }

    override fun onReady(rapidsConnection: RapidsConnection) {
        start()
    }

    override fun onShutdown(rapidsConnection: RapidsConnection) {
        stop()
    }

    fun start() {
        logger.info { "Starter ArbeidssøkerStatusLytter" }
        if (!consumerThread.isAlive) {
            consumerThread.start()
        }
    }

    fun stop() {
        logger.info { "Stopper ArbeidssøkerStatusLytter" }
        running.set(false)
        consumer.wakeup() // Interrupts poll() safely
        consumerThread.join() // Ensure thread shutdown
        rapidsConnection.stop()
    }

    private fun consumeLoop() {
        try {
            while (running.get()) {
                val records = consumer.poll(Duration.ofSeconds(1))
                processRecords(records)
            }
        } catch (e: WakeupException) {
            if (running.get()) throw e
        } catch (e: Exception) {
            logger.error(e) { "Noe feil skjedde i consumeringen" }
        } finally {
            closeResources()
        }
    }

    private fun processRecords(records: ConsumerRecords<Long, T>) {
        if (records.isEmpty) return

        val currentPositions =
            records
                .groupBy { TopicPartition(it.topic(), it.partition()) }
                .mapValues { partition -> partition.value.minOf { it.offset() } }
                .toMutableMap()

        try {
            val korrelasjonsId = UUIDv7.newUuid()
            records.forEach { record ->
                block(korrelasjonsId, record.value())

                currentPositions[TopicPartition(record.topic(), record.partition())] = record.offset() + 1
            }
        } catch (err: Exception) {
            logger.info(err) { "Feil ved behandling av meldinger. Tilbakestiller offsets: $currentPositions" }
            currentPositions.forEach { (partition, offset) -> consumer.seek(partition, offset) }
            stop()
        } finally {
            consumer.commitSync()
        }
    }

    private fun closeResources() {
        tryAndLog(consumer::unsubscribe)
        tryAndLog(consumer::close)
    }

    private fun tryAndLog(block: () -> Unit) {
        try {
            block()
        } catch (err: Exception) {
            logger.error(err) { err.message }
        }
    }
}
