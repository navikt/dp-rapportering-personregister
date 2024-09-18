package no.nav.dagpenger.rapportering.personregister.modell

import java.time.LocalDateTime

class Person(
    val ident: String,
) {
    private val hendelser: TemporalCollection<Hendelse> = TemporalCollection()

    constructor(ident: String, hendelse: Hendelse) : this(ident) {
        hendelser.put(hendelse.dato, hendelse)
    }

    fun behandle(hendelse: Hendelse) {
        hendelser.put(LocalDateTime.now(), hendelse)
    }

    val status: Status
        get() = hendelser.get(LocalDateTime.now()).status

    val statusForDato: (LocalDateTime) -> Status = { hendelser.get(it).status }
}
