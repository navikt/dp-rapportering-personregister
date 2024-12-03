package no.nav.dagpenger.rapportering.personregister.mediator.kafka.konsument

interface KafkaKonsument {
    val topic: String

    fun stream()

    fun close()
}
