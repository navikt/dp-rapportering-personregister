package no.nav.dagpenger.rapportering.personregister.modell

import no.nav.dagpenger.rapportering.personregister.modell.Status.DAGPENGERBRUKER
import no.nav.dagpenger.rapportering.personregister.modell.Status.IKKE_DAGPENGERBRUKER
import java.time.LocalDateTime
import java.util.UUID

enum class Status {
    DAGPENGERBRUKER,
    IKKE_DAGPENGERBRUKER,
}

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

    fun status(dato: LocalDateTime): Status = if (statusHistorikk.isEmpty()) IKKE_DAGPENGERBRUKER else statusHistorikk.get(dato)

    val status: Status
        get() = status(LocalDateTime.now())

    fun setStatus(status: Status) {
        statusHistorikk.put(LocalDateTime.now(), status)
    }

    fun behandle(hendelse: Hendelse) {
        hendelser.add(hendelse)
        hendelse.behandle(this)
    }
}

fun Person.overtaArbeidssøkerBekreftelse() {
    arbeidssøkerperioder.gjeldende?.let {
        if (it.overtattBekreftelse != true) {
            try {
                observers.forEach { observer -> observer.overtaArbeidssøkerBekreftelse(this) }
                it.overtattBekreftelse = true
            } catch (e: Exception) {
                it.overtattBekreftelse = false
            }
        }
    }
}

fun Person.frasiArbeidssøkerBekreftelse(periodeId: UUID) {
    arbeidssøkerperioder
        .find { it.periodeId == periodeId }
        ?.let {
            if (it.overtattBekreftelse == true) {
                observers.forEach { observer -> observer.frasiArbeidssøkerBekreftelse(this) }
                it.overtattBekreftelse = false
            }
        }
}

val Person.erArbeidssøker: Boolean
    get() = arbeidssøkerperioder.gjeldende != null

fun Person.vurderNyStatus() = if (this.oppfyllerKrav) DAGPENGERBRUKER else IKKE_DAGPENGERBRUKER

val Person.oppfyllerKrav: Boolean get() = this.erArbeidssøker && this.meldeplikt && this.meldegruppe == "DAGP"
