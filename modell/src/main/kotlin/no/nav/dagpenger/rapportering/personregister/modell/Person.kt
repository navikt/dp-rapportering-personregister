package no.nav.dagpenger.rapportering.personregister.modell

import java.time.LocalDateTime

data class Person(
    val ident: String,
) {
    val hendelser = mutableListOf<Hendelse>()

    val statusHistorikk = TemporalCollection<Status>()

    val status: Status
        get() = status(LocalDateTime.now())

    fun status(dato: LocalDateTime): Status = statusHistorikk.get(dato)

    fun behandle(hendelse: Hendelse) {
        hendelser.add(hendelse)
        statusHistorikk.put(hendelse.dato, hendelse.status)
    }
}
