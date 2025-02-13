package no.nav.dagpenger.rapportering.personregister.modell

import java.time.LocalDateTime

data class Person(
    val ident: String,
    val dagpengerstatusHistorikk: TemporalCollection<DagpengerStatus> = TemporalCollection(),
    val arbeidssøkerperioder: MutableList<Arbeidssøkerperiode> = mutableListOf(),
) {
    val hendelser = mutableListOf<Hendelse>()
    val observers = mutableListOf<PersonObserver>()

    fun addObserver(observer: PersonObserver) {
        observers.add(observer)
    }

    fun dagpengerstatus(dato: LocalDateTime): DagpengerStatus =
        if (dagpengerstatusHistorikk.isEmpty()) INAKTIV else dagpengerstatusHistorikk.get(dato)

    val dagpengerstatus: DagpengerStatus
        get() = dagpengerstatus(LocalDateTime.now())

    fun behandle(hendelse: Hendelse) {
        dagpengerstatusHistorikk.takeIf { it.isEmpty() }?.put(hendelse.dato, INAKTIV)

        dagpengerstatus
            .håndter(hendelse)
            .takeIf { nyStatus -> nyStatus != dagpengerstatus }
            ?.also { dagpengerstatusHistorikk.put(hendelse.dato, it) }
//            ?.takeIf { nyStatus -> nyStatus == STANSET }
            ?.let { observers.forEach { observer -> observer.frasiArbeidssøkerBekreftelse(this) } }

        hendelser.add(hendelse)
    }
}
