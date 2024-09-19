package no.nav.dagpenger.rapportering.personregister.modell

import java.time.LocalDateTime

data class Person(
    val ident: String,
) {
    val hendelser: TemporalCollection<Hendelse> = TemporalCollection()

    fun behandle(hendelse: Hendelse) {
        hendelser.put(hendelse.dato, hendelse)
    }

    val status: Status
        get() = hendelser.get(LocalDateTime.now()).status

    val statusForDato: (LocalDateTime) -> Status = { hendelser.get(it).status }
}
