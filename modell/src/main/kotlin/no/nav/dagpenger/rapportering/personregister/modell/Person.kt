package no.nav.dagpenger.rapportering.personregister.modell

import java.time.LocalDateTime

data class Person(
    val ident: String,
    val statusHistorikk: TemporalCollection<Status> = TemporalCollection(),
    val arbeidssøkerperioder: MutableList<Arbeidssøkerperiode> = mutableListOf(),
) {
    val hendelser = mutableListOf<Hendelse>()
    val observers = mutableListOf<PersonObserver>()

    fun addObserver(observer: PersonObserver) {
        observers.add(observer)
    }

    fun status(dato: LocalDateTime): Status = if (statusHistorikk.isEmpty()) IkkeDagpengerbruker else statusHistorikk.get(dato)

    val status: Status
        get() = status(LocalDateTime.now())

    fun behandle(hendelse: SøknadHendelse) {
        status.håndter(hendelse) { nyStatus ->
            statusHistorikk.put(hendelse.dato, nyStatus)
            hendelser.add(hendelse)
        }
    }

    fun behandle(hendelse: DagpengerMeldegruppeHendelse) {
        status.håndter(hendelse) { nyStatus ->
            statusHistorikk.put(hendelse.dato, nyStatus)
            hendelser.add(hendelse)
        }
    }

    fun behandle(hendelse: AnnenMeldegruppeHendelse) {
        status.håndter(hendelse) { nyStatus ->
            observers.forEach { observer -> observer.frasiArbeidssøkerBekreftelse(this) }
            statusHistorikk.put(hendelse.dato, nyStatus)
            arbeidssøkerperioder.gjeldende?.let { it.overtattBekreftelse = false }
        }
    }
}
