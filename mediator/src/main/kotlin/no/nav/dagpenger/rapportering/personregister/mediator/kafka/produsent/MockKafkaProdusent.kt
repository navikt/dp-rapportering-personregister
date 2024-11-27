package no.nav.dagpenger.rapportering.personregister.mediator.kafka.produsent

import java.util.concurrent.ConcurrentLinkedQueue

class MockKafkaProdusent<T>(
    topic: String,
) : KafkaProdusent<T>(topic) {
    val sentMessages = ConcurrentLinkedQueue<T>()

    override fun send(value: T): Pair<Int, Long> {
        sentMessages.add(value)
        return 0 to (sentMessages.size - 1).toLong()
    }

    fun getSentMessages(): List<T> = sentMessages.toList()

    fun reset() = sentMessages.clear()

    override fun close() {}
}
