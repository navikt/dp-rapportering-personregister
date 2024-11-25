package no.nav.dagpenger.rapportering.personregister.mediator.kafka.produsent

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

abstract class KafkaProdusent<T>(
    private val topic: String,
    private val objectMapper: ObjectMapper = jacksonObjectMapper(),
) {
    protected fun serialize(value: T): String = objectMapper.writeValueAsString(value)

    abstract fun send(value: T): Pair<Int, Long>

    abstract fun close()
}
