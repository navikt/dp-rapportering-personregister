package no.nav.dagpenger.rapportering.personregister.mediator.kafka

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.apache.kafka.clients.consumer.CloseOptions
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.errors.WakeupException
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class KafkaLytterTest {
    private val topic = "test-topic"
    private val partition = TopicPartition(topic, 0)

    private fun records(vararg offsets: Long) =
        ConsumerRecords(
            mapOf(partition to offsets.map { ConsumerRecord(topic, 0, it, 0L, "verdi") }),
            mapOf(partition to OffsetAndMetadata(offsets.max() + 1)),
        )

    @Test
    fun `behandlingsfeil committer behandlede offsets, lukker konsumenten og stopper appen uten deadlock`() {
        val consumer = assertStopsAfterFailure(block = { _, _ -> error("Simulert behandlingsfeil") }) { records(5) }

        verify(exactly = 1) { consumer.seek(partition, 5L) }
        verify(exactly = 1) { consumer.commitSync(mapOf(partition to OffsetAndMetadata(5L))) }
        verify(exactly = 0) { consumer.commitSync() }
    }

    @Test
    fun `delvis behandlet batch committer bare offsets etter siste vellykkede melding`() {
        var antall = 0
        val consumer =
            assertStopsAfterFailure(block = { _, _ -> if (++antall == 2) error("Simulert behandlingsfeil") }) {
                records(3, 4)
            }

        verify(exactly = 1) { consumer.seek(partition, 4L) }
        verify(exactly = 1) { consumer.commitSync(mapOf(partition to OffsetAndMetadata(4L))) }
    }

    @Test
    fun `poll-feil lukker konsumenten og stopper appen`() {
        assertStopsAfterFailure { throw IllegalStateException("Simulert poll-feil") }
    }

    @Test
    fun `uventet wakeup stopper appen`() {
        assertStopsAfterFailure { throw WakeupException() }
    }

    @Test
    fun `normal shutdown lukker konsumenten uten å stoppe appen på nytt`() {
        val polling = CountDownLatch(1)
        val wakeupSignal = CountDownLatch(1)
        val consumer =
            mockk<Consumer<Long, String>>(relaxed = true) {
                every { poll(any<Duration>()) } answers {
                    polling.countDown()
                    wakeupSignal.await()
                    throw WakeupException()
                }
                every { wakeup() } answers { wakeupSignal.countDown() }
            }
        val rapid = mockk<RapidsConnection>(relaxed = true)
        val lytter = KafkaLytter(consumer, rapid) { _, _ -> }

        lytter.start()
        try {
            polling.await(2, TimeUnit.SECONDS) shouldBe true
            assertTimeoutPreemptively(Duration.ofSeconds(2)) { lytter.onShutdown(rapid) }
            verify(exactly = 1) { consumer.unsubscribe() }
            verify(exactly = 1) { consumer.close(any<CloseOptions>()) }
            verify(exactly = 0) { rapid.stop() }
        } finally {
            wakeupSignal.countDown()
        }
    }

    @Test
    fun `feil ved seek committer ikke ubehandlede offsets`() {
        val consumer =
            assertStopsAfterFailure(
                block = { _, _ -> error("Simulert behandlingsfeil") },
                configureConsumer = { every { it.seek(partition, 5L) } throws IllegalStateException("Simulert seek-feil") },
            ) { records(5) }

        verify(exactly = 1) { consumer.commitSync(mapOf(partition to OffsetAndMetadata(5L))) }
        verify(exactly = 0) { consumer.commitSync() }
    }

    @Test
    fun `Error lukker konsumenten og stopper appen`() {
        assertStopsAfterFailure { throw AssertionError("Simulert Error") }
    }

    @Test
    fun `appstopp venter ikke ubegrenset på ressurslukking`() {
        val closing = CountDownLatch(1)
        val releaseClose = CountDownLatch(1)
        val stopped = CountDownLatch(1)
        val consumerThread = AtomicReference<Thread>()
        val consumer =
            mockk<Consumer<Long, String>>(relaxed = true) {
                every { poll(any<Duration>()) } answers {
                    consumerThread.set(Thread.currentThread())
                    throw IllegalStateException("Simulert poll-feil")
                }
                every { close(any<CloseOptions>()) } answers {
                    closing.countDown()
                    releaseClose.await()
                }
            }
        val rapid = mockk<RapidsConnection>(relaxed = true)
        val lytter = KafkaLytter(consumer, rapid) { _, _ -> }
        every { rapid.stop() } answers {
            lytter.onShutdown(rapid)
            stopped.countDown()
        }

        lytter.start()
        try {
            closing.await(2, TimeUnit.SECONDS) shouldBe true
            stopped.await(7, TimeUnit.SECONDS) shouldBe true
            consumerThread.get().isAlive shouldBe true
            verify(exactly = 1) { rapid.stop() }
        } finally {
            releaseClose.countDown()
            consumerThread.get()?.join(2_000)
        }
        consumerThread.get().isAlive shouldBe false
    }

    @Test
    fun `normal shutdown har tidsgrense selv når poll ikke avbrytes`() {
        val polling = CountDownLatch(1)
        val releasePoll = CountDownLatch(1)
        val consumerThread = AtomicReference<Thread>()
        val consumer =
            mockk<Consumer<Long, String>>(relaxed = true) {
                every { poll(any<Duration>()) } answers {
                    consumerThread.set(Thread.currentThread())
                    polling.countDown()
                    releasePoll.await()
                    ConsumerRecords.empty()
                }
            }
        val rapid = mockk<RapidsConnection>(relaxed = true)
        val lytter = KafkaLytter(consumer, rapid) { _, _ -> }

        lytter.start()
        try {
            polling.await(2, TimeUnit.SECONDS) shouldBe true
            assertTimeoutPreemptively(Duration.ofSeconds(7)) { lytter.onShutdown(rapid) }
            consumerThread.get().isAlive shouldBe true
            verify(exactly = 0) { rapid.stop() }
        } finally {
            releasePoll.countDown()
            consumerThread.get()?.join(2_000)
        }
        consumerThread.get().isAlive shouldBe false
        verify(exactly = 1) { consumer.close(any<CloseOptions>()) }
    }

    private fun assertStopsAfterFailure(
        block: (java.util.UUID, String) -> Unit = { _, _ -> },
        configureConsumer: (Consumer<Long, String>) -> Unit = {},
        pollRecords: () -> ConsumerRecords<Long, String>,
    ): Consumer<Long, String> {
        val consumerThread = AtomicReference<Thread>()
        val stopped = CountDownLatch(1)
        val consumer =
            mockk<Consumer<Long, String>>(relaxed = true) {
                every { poll(any<Duration>()) } answers {
                    consumerThread.set(Thread.currentThread())
                    pollRecords()
                }
            }
        configureConsumer(consumer)
        val rapid = mockk<RapidsConnection>(relaxed = true)
        val lytter = KafkaLytter(consumer, rapid, block)
        every { rapid.stop() } answers {
            // Simulerer at RapidsConnection.stop() kaller onShutdown på en annen tråd
            val shutdownCallback = Thread { lytter.onShutdown(rapid) }
            shutdownCallback.start()
            shutdownCallback.join(2_000)
            shutdownCallback.isAlive shouldBe false
            stopped.countDown()
        }

        lytter.start()
        try {
            stopped.await(2, TimeUnit.SECONDS) shouldBe true
            consumerThread.get().join(2_000)
            consumerThread.get().isAlive shouldBe false
            verify(exactly = 1) { rapid.stop() }
            verify(exactly = 1) { consumer.unsubscribe() }
            verify(exactly = 1) { consumer.close(any<CloseOptions>()) }
        } finally {
            consumerThread.get()?.let {
                it.interrupt()
                it.join(2_000)
            }
        }
        return consumer
    }
}
