package no.nav.dagpenger.rapportering.personregister.mediator.kafka

import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.MockProducer
import org.apache.kafka.clients.producer.ProducerRecord

interface KafkaProdusent {
    fun publiser(
        verdi: String,
        headers: Map<String, ByteArray>? = emptyMap(),
    ): Pair<Int, Long>

    fun close() = Unit
}

class KafkaProdusentImpl(
    private val kafka: KafkaProducer<String, String>,
    private val topic: String,
) : KafkaProdusent {
    override fun publiser(
        verdi: String,
        headers: Map<String, ByteArray>?,
    ): Pair<Int, Long> =
        kafka
            .send(
                ProducerRecord<String, String>(topic, verdi).also {
                    headers?.forEach { h ->
                        it.headers().add(h.key, h.value)
                    }
                },
            ).get()
            .let {
                it.partition() to it.offset()
            }

    override fun close() {
        kafka.close()
    }
}

class TestProdusent(
    private val kafka: MockProducer<String, String>,
    private val topic: String,
) : KafkaProdusent {
    data class Record(
        val verdi: String,
        val headers: Map<String, ByteArray>?,
    )

    var closed = false
        private set
    val meldinger = mutableListOf<Record>()

    override fun publiser(
        verdi: String,
        headers: Map<String, ByteArray>?,
    ): Pair<Int, Long> =
        kafka
            .send(
                ProducerRecord<String, String>(topic, verdi).also {
                    headers?.forEach { h ->
                        it.headers().add(h.key, h.value)
                    }
                },
            ).get()
            .let { it.partition() to it.offset() }

    fun reset() = meldinger.clear()

    override fun close() {
        closed = true
    }
}
