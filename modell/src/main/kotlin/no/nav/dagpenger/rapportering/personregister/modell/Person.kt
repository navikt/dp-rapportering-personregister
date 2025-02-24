package no.nav.dagpenger.rapportering.personregister.modell

import java.time.LocalDateTime

data class Person(
    val ident: String,
    val statusHistorikk: TemporalCollection<Status> = TemporalCollection(),
    val arbeidssøkerperioder: MutableList<Arbeidssøkerperiode> = mutableListOf(),
) {
    var meldegruppe: String? = null
    var meldeplikt: Boolean = false

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

    fun behandle(hendelse: ArbeidssøkerperiodeHendelse) = behandle(hendelse) { overtaArbeidssøkerBekreftelse() }

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
        status
            .håndter(hendelse, this) { nyStatus ->
                statusHistorikk.put(hendelse.dato, nyStatus)
                håndter(hendelse)
            }.also { hendelser.add(hendelse) }
    }
}

fun Person.overtaArbeidssøkerBekreftelse() {
    arbeidssøkerperioder.gjeldende?.let {
        if (it.overtattBekreftelse != true) {
            try {
                observers.forEach { observer -> observer.overtaArbeidssøkerBekreftelse(this) }
                // TODO: Hva skjer hvis overtaArbeidssøkerBekreftelse feiler?
                it.overtattBekreftelse = true
            } catch (e: Exception) {
                it.overtattBekreftelse = false
            }
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

val Person.erArbeidssøker: Boolean
    get() = arbeidssøkerperioder.gjeldende != null

val Person.oppfyllerKrav: Boolean get() = this.erArbeidssøker && this.meldeplikt && this.meldegruppe == "DAGP"
