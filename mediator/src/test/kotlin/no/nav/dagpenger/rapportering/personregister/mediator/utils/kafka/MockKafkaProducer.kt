package no.nav.dagpenger.rapportering.personregister.mediator.utils.kafka

import no.nav.paw.bekreftelse.paavegneav.v1.PaaVegneAv
import org.apache.kafka.clients.consumer.ConsumerGroupMetadata
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.clients.producer.Callback
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.Metric
import org.apache.kafka.common.MetricName
import org.apache.kafka.common.PartitionInfo
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.Uuid
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

class TestKafkaProducer<T>(
    private val topic: String,
    container: TestKafkaContainer,
) {
    val producer: Producer<Long, PaaVegneAv> = container.createProducer()

    fun send(
        key: Long,
        message: PaaVegneAv,
    ) {
        producer.send(ProducerRecord(topic, key, message))
        producer.flush()
    }
}

class MockKafkaProducer<T> : Producer<Long, T> {
    private var isClosed = false
    private val _meldinger = mutableListOf<ProducerRecord<Long, T>>()

    val meldinger: List<ProducerRecord<Long, T>> get() = _meldinger

    override fun close() {}

    override fun close(p0: Duration?) {}

    override fun initTransactions() {}

    override fun beginTransaction() {}

    @Deprecated("Deprecated in Java")
    override fun sendOffsetsToTransaction(
        p0: MutableMap<TopicPartition, OffsetAndMetadata>?,
        p1: String?,
    ) {}

    override fun sendOffsetsToTransaction(
        p0: MutableMap<TopicPartition, OffsetAndMetadata>?,
        p1: ConsumerGroupMetadata?,
    ) {}

    override fun commitTransaction() {}

    override fun abortTransaction() {}

    override fun flush() {
        _meldinger.clear()
    }

    override fun partitionsFor(p0: String?): MutableList<PartitionInfo> = mutableListOf()

    override fun metrics(): MutableMap<MetricName, out Metric> = mutableMapOf()

    override fun clientInstanceId(p0: Duration?): Uuid = Uuid.randomUuid()

    override fun send(
        p0: ProducerRecord<Long, T>?,
        p1: Callback?,
    ): Future<RecordMetadata> {
        check(!isClosed) { "Cannot send message. Producer is closed." }
        if (p0 != null) _meldinger.add(p0)
        val metadata = RecordMetadata(TopicPartition(p0!!.topic(), 0), 0, 0, 0, 0, 0)
        val future = CompletableFuture<RecordMetadata>()
        future.complete(metadata)
        p1?.onCompletion(metadata, null)
        return future
    }

    override fun send(p0: ProducerRecord<Long, T>?): Future<RecordMetadata> = send(p0, null)
}
