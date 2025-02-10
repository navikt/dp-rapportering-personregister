package no.nav.dagpenger.rapportering.personregister.modell

import java.time.LocalDateTime

interface PersonObserver {
    fun ovetaArbeidssøkerBekreftelse(person: Person) {}

    fun frasiArbeidssøkerBekreftelse(person: Person)
}

data class Person(
    val ident: String,
    val statusHistorikk: TemporalCollection<Status> = TemporalCollection(),
) {
    val observers = mutableListOf<PersonObserver>()

    val arbeidssøkerperioder = mutableListOf<Arbeidssøkerperiode>()

    fun addObserver(observer: PersonObserver) {
        observers.add(observer)
    }

    fun status(dato: LocalDateTime): Status = statusHistorikk.get(dato)

    val status: Status
        get() = status(LocalDateTime.now())

    val hendelser = mutableListOf<Hendelse>()

    fun behandle(hendelse: Hendelse) {
        statusHistorikk.takeIf { it.isEmpty() }?.put(hendelse.dato, SØKT)

        status
            .håndter(hendelse)
            .takeIf { nyStatus -> nyStatus != status }
            ?.also { statusHistorikk.put(hendelse.dato, it) }
            ?.takeIf { nyStatus -> nyStatus == STANSET }
            ?.let { observers.forEach { observer -> observer.frasiArbeidssøkerBekreftelse(this) } }

        hendelser.add(hendelse)
    }
}
