package no.nav.dagpenger.rapportering.personregister.mediator.kafka.produsent

class MockKafkaProdusent<T>(
    topic: String,
) : KafkaProdusent<T>(topic) {
    private val sentMessages = mutableListOf<T>()

    override fun send(value: T): Pair<Int, Long> {
        sentMessages.add(value)
        return 0 to (sentMessages.size - 1).toLong()
    }

    fun getSentMessages(): List<T> = sentMessages

    fun reset() = sentMessages.clear()

    override fun close() {}
}
