package no.nav.dagpenger.rapportering.personregister.mediator.kafka.producer

import no.nav.dagpenger.rapportering.personregister.mediator.Configuration.defaultObjectMapper

abstract class KafkaProdusent<T> {
    protected fun serialize(value: T): String = defaultObjectMapper.writeValueAsString(value)

    abstract fun send(
        key: String,
        value: T,
    )

    abstract fun close()
}
