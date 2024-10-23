package no.nav.dagpenger.rapportering.personregister.modell

import SimpleStatusStrategyFactory
import java.time.LocalDateTime

data class Person(
    val ident: String,
) {
    val hendelser = mutableListOf<Hendelse>()
    val statusHistorikk = TemporalCollection<Status>()
    private var arbeidssøker: Boolean? = null

    fun status(dato: LocalDateTime): Status = statusHistorikk.get(dato)

    val status: Status
        get() = status(LocalDateTime.now())

    fun behandle(hendelse: Hendelse) {
        hendelser.add(hendelse)

        SimpleStatusStrategyFactory()
            .createStrategy(this)
            .also { it.håndter(this, hendelse) }
    }

    fun erArbeidssøker(): Boolean? = arbeidssøker

    fun settArbeidssøker(arbeidssøker: Boolean) {
        this.arbeidssøker = arbeidssøker
    }
}
