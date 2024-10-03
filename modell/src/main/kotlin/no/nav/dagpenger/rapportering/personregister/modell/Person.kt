package no.nav.dagpenger.rapportering.personregister.modell

import java.time.LocalDateTime

data class Person(
    val ident: String,
) {
    val hendelser = mutableListOf<Hendelse>()
    val statusHistorikk = TemporalCollection<Status>()

    fun status(dato: LocalDateTime): Status = statusHistorikk.get(dato)

    val status: Status
        get() = if (statusHistorikk.isNotEmpty()) status(LocalDateTime.now()) else Status.IKKE_REGISTRERT

    fun behandle(hendelse: Hendelse) {
        hendelser.add(hendelse)

        SimpleStatusStrategyFactory()
            .createStrategy(this)
            .also { it.håndter(this, hendelse) }
    }
}
