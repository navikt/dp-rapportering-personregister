package no.nav.dagpenger.rapportering.personregister.modell

import java.time.LocalDateTime
import java.util.UUID

data class Arbeidssøkerperiode(
    val periodeId: UUID,
    val ident: String,
    val startet: LocalDateTime,
    var avsluttet: LocalDateTime?,
    var overtattBekreftelse: Boolean?,
)

sealed class ArbeidssøkerperiodeHendelse(
    open val periodeId: UUID,
    open val ident: String,
) {
    abstract fun håndter(person: Person)
}

data class StartetArbeidssøkerperiodeHendelse(
    override val periodeId: UUID,
    override val ident: String,
    val startet: LocalDateTime,
) : ArbeidssøkerperiodeHendelse(periodeId, ident) {
    override fun håndter(person: Person) {
        person.arbeidssøkerperioder
            .find { it.periodeId == periodeId }
            ?.let {
                if (it.overtattBekreftelse != true) {
                    it.overtattBekreftelse = true
                    person.overtaArbeidssøkerBekreftelse()
                }
            }
            ?: run {
                person.arbeidssøkerperioder.add(
                    Arbeidssøkerperiode(periodeId, ident, startet, avsluttet = null, overtattBekreftelse = true),
                )
                person.overtaArbeidssøkerBekreftelse()
            }
    }
}

data class AvsluttetArbeidssøkerperiodeHendelse(
    override val periodeId: UUID,
    override val ident: String,
    val startet: LocalDateTime,
    val avsluttet: LocalDateTime,
) : ArbeidssøkerperiodeHendelse(periodeId, ident) {
    override fun håndter(person: Person) {
        person.arbeidssøkerperioder
            .find { it.periodeId == periodeId }
            ?.let {
                it.avsluttet = avsluttet
            }
            ?: person.arbeidssøkerperioder.add(Arbeidssøkerperiode(periodeId, ident, startet, avsluttet, overtattBekreftelse = false))
    }
}

fun Arbeidssøkerperiode.aktiv(): Boolean = avsluttet == null

val List<Arbeidssøkerperiode>.gjeldende: Arbeidssøkerperiode?
    get() = this.firstOrNull { it.aktiv() }
