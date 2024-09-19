package no.nav.dagpenger.rapportering.personregister.modell

import java.time.LocalDateTime

class Person private constructor(
    val ident: String,
    val hendelser: TemporalCollection<Hendelse> = TemporalCollection(),
) {
    constructor(ident: String) : this(ident, TemporalCollection())

    constructor(ident: String, hendelse: Hendelse) : this(ident) {
        hendelser.put(hendelse.dato, hendelse)
    }

    fun behandle(hendelse: Hendelse) {
        hendelser.put(hendelse.dato, hendelse)
    }

    val status: Status
        get() = hendelser.get(LocalDateTime.now()).status

    val statusForDato: (LocalDateTime) -> Status = { hendelser.get(it).status }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Person) return false

        return ident == other.ident &&
            hendelser.allItems() == other.hendelser.allItems()
    }

    override fun hashCode(): Int {
        var result = ident.hashCode()
        result = 31 * result + hendelser.hashCode()
        result = 31 * result + statusForDato.hashCode()
        return result
    }
}
