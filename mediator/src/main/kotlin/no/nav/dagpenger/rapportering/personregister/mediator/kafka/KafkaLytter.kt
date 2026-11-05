package no.nav.dagpenger.rapportering.personregister.mediator.kafka

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.utils.UUIDv7
import org.apache.kafka.clients.consumer.CloseOptions
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.OffsetAndMetadata
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
    private val applicationStopping = AtomicBoolean(false)
    private val consumerThread: Thread

    init {
        consumerThread = Thread { consumeLoop() }.apply { isDaemon = true }
    }

    private companion object {
        val shutdownTimeout: Duration = Duration.ofSeconds(5)
        val logger = KotlinLogging.logger {}
    }

    override fun onReady(rapidsConnection: RapidsConnection) {
        start()
    }

    override fun onShutdown(rapidsConnection: RapidsConnection) {
        if (applicationStopping.compareAndSet(false, true)) {
            stopConsumer()
        }
    }

    fun start() {
        logger.info { "Starter KafkaLytter" }
        if (!consumerThread.isAlive) {
            consumerThread.start()
        }
    }

    fun stop() {
        stopConsumer()
        stopApplication()
    }

    private fun stopConsumer() {
        logger.info { "Stopper KafkaLytter" }
        if (running.getAndSet(false)) {
            consumer.wakeup() // Interrupts poll() safely
        }
        awaitConsumerShutdown()
    }

    private fun awaitConsumerShutdown() {
        // Å vente på egen tråd gir deadlock
        if (Thread.currentThread() == consumerThread) return
        consumerThread.join(shutdownTimeout.toMillis())
        if (consumerThread.isAlive) {
            logger.warn { "Konsumenten avsluttet ikke innen tidsfristen. Fortsetter appstopp." }
        }
    }

    private fun stopApplication() {
        if (applicationStopping.compareAndSet(false, true)) {
            rapidsConnection.stop()
        }
    }

    private fun consumeLoop() {
        var failed = true
        try {
            while (running.get()) {
                val records = consumer.poll(Duration.ofSeconds(1))
                processRecords(records)
            }
            failed = false
        } catch (e: Throwable) {
            failed = e !is WakeupException || running.get()
            if (failed) {
                logger.error(e) { "Noe feil skjedde i consumeringen" }
            }
        } finally {
            running.set(false)
            if (failed) {
                // Shutdown-callbacks kan vente på consumer-tråden, så appen må stoppes fra en egen tråd
                Thread(
                    {
                        awaitConsumerShutdown()
                        stopApplication()
                    },
                    "kafka-lytter-shutdown",
                ).apply {
                    isDaemon = false
                    start()
                }
            }
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
            logger.error(err) { "Feil ved behandling av meldinger. Tilbakestiller offsets: $currentPositions" }
            currentPositions.forEach { (partition, offset) -> consumer.seek(partition, offset) }
            throw err
        } finally {
            consumer.commitSync(currentPositions.mapValues { (_, offset) -> OffsetAndMetadata(offset) })
        }
    }

    private fun closeResources() {
        tryAndLog(consumer::unsubscribe)
        tryAndLog { consumer.close(CloseOptions.timeout(shutdownTimeout)) }
    }

    private fun tryAndLog(block: () -> Unit) {
        try {
            block()
        } catch (err: Exception) {
            logger.error(err) { err.message }
        }
    }
}
