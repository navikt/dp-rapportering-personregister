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

    fun behandle(hendelse: MeldepliktHendelse) = behandle(hendelse) {}

    fun behandle(hendelse: ArbeidssøkerperiodeHendelse) = hendelse.håndter(this)

    fun behandle(hendelse: Hendelse) {
        when (hendelse) {
            is SøknadHendelse -> behandle(hendelse)
            is DagpengerMeldegruppeHendelse -> behandle(hendelse)
            is AnnenMeldegruppeHendelse -> behandle(hendelse)
            is MeldepliktHendelse -> behandle(hendelse)
            else -> throw IllegalArgumentException("Ukjent hendelse: ${hendelse::class.simpleName}")
        }
    }

    private fun behandle(
        hendelse: Hendelse,
        håndter: (Hendelse) -> Unit = {},
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
            // TODO: Hva skjer hvis overtaArbeidssøkerBekreftelse feiler?
            it.overtattBekreftelse = true
        }
    }
}

fun Person.frasiArbeidssøkerBekreftelse() {
    arbeidssøkerperioder.gjeldende?.let {
        if (it.overtattBekreftelse == true) {
            observers.forEach { observer -> observer.frasiArbeidssøkerBekreftelse(this) }
            // TODO: Hva skjer hvis frasiArbeidssøkerBekreftelse feiler?
            it.overtattBekreftelse = false
        }
    }
}
