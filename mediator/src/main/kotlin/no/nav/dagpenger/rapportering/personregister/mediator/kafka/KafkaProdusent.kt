package no.nav.dagpenger.rapportering.personregister.mediator.kafka

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

interface KafkaProdusent {
    fun publiser(
        verdi: JsonMessage,
        headers: Map<String, ByteArray>? = emptyMap(),
    ): Pair<Int, Long>

    fun close() = Unit
}

class KafkaProdusentImpl(
    private val kafka: KafkaProducer<String, JsonMessage>,
    private val topic: String,
) : KafkaProdusent {
    override fun publiser(
        verdi: JsonMessage,
        headers: Map<String, ByteArray>?,
    ): Pair<Int, Long> =
        kafka
            .send(
                ProducerRecord<String, JsonMessage>(topic, verdi).also {
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

class TestProdusent : KafkaProdusent {
    data class Record(
        val verdi: JsonMessage,
        val headers: Map<String, ByteArray>?,
    )

    var closed = false
        private set
    val meldinger = mutableListOf<Record>()

    override fun publiser(
        verdi: JsonMessage,
        headers: Map<String, ByteArray>?,
    ): Pair<Int, Long> {
        if (closed) {
            throw IllegalStateException("Produsenten er lukket")
        }
        return meldinger.let {
            it.add(Record(verdi, headers))
            Pair(0, (it.size - 1).toLong())
        }
    }

    override fun close() {
        closed = true
    }
}
