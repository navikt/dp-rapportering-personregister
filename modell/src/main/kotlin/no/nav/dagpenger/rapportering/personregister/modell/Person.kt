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

    fun behandle(hendelse: SøknadHendelse) = behandle(hendelse) {}

    fun behandle(hendelse: DagpengerMeldegruppeHendelse) = behandle(hendelse) { overtaArbeidssøkerBekreftelse() }

    fun behandle(hendelse: AnnenMeldegruppeHendelse) = behandle(hendelse) { frasiArbeidssøkerBekreftelse() }

    fun behandle(hendelse: ArbeidssøkerperiodeHendelse) = hendelse.håndter(this)

    fun <T : Hendelse> behandle(
        hendelse: T,
        håndter: (T) -> Unit = {},
    ) {
        status.håndter(hendelse) { nyStatus ->
            statusHistorikk.put(hendelse.dato, nyStatus)
            hendelser.add(hendelse)
            håndter(hendelse)
        }
    }
}

fun Person.overtaArbeidssøkerBekreftelse() {
    arbeidssøkerperioder.gjeldende?.let {
        if (it.overtattBekreftelse != true) {
            observers.forEach { observer -> observer.overtaArbeidssøkerBekreftelse(this) }
            it.overtattBekreftelse = true
        }
    }
}

fun Person.frasiArbeidssøkerBekreftelse() {
    arbeidssøkerperioder.gjeldende?.let {
        if (it.overtattBekreftelse == true) {
            observers.forEach { observer -> observer.frasiArbeidssøkerBekreftelse(this) }
            it.overtattBekreftelse = false
        }
    }
}
