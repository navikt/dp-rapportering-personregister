package no.nav.dagpenger.rapportering.personregister.modell

import java.time.LocalDateTime

data class Person(
    val ident: String,
    val statusHistorikk: TemporalCollection<Status> = TemporalCollection(),
) {
    fun status(dato: LocalDateTime): Status = statusHistorikk.get(dato)

    val status: Status
        get() = status(LocalDateTime.now())

    val hendelser = mutableListOf<Hendelse>()

    fun behandle(hendelse: Hendelse) {
        if (statusHistorikk.isEmpty()) {
            statusHistorikk.put(hendelse.dato, SØKT)
        }

        status
            .håndter(hendelse)
            .takeIf { it != status }
            ?.let { statusHistorikk.put(hendelse.dato, it) }

        hendelser.add(hendelse)
    }
}
