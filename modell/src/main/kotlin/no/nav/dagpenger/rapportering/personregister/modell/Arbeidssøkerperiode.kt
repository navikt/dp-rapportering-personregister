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

abstract class ArbeidssøkerperiodeHendelse(
    open val periodeId: UUID,
    override val ident: String,
    override val dato: LocalDateTime,
) : Hendelse {
    override val referanseId by lazy { periodeId.toString() }

    override fun behandle(person: Person) {}

    override val kilde: Kildesystem = Kildesystem.Arbeidssokerregisteret
}

data class StartetArbeidssøkerperiodeHendelse(
    override val periodeId: UUID,
    override val ident: String,
    val startet: LocalDateTime,
) : ArbeidssøkerperiodeHendelse(periodeId, ident, startet) {
    override fun behandle(person: Person) {
        person.arbeidssøkerperioder
            .none { it.periodeId == periodeId }
            .takeIf { it }
            ?.let { person.leggTilNyArbeidssøkerperiode(this) }

        person
            .vurderNyStatus()
            .takeIf { it != person.status }
            ?.let {
                person.setStatus(it)
                if (person.oppfyllerKrav) {
                    person.overtaArbeidssøkerBekreftelse()
                }
                /* else {
                    person.frasiArbeidssøkerBekreftelse(periodeId)
                } */
            }
    }
}

data class AvsluttetArbeidssøkerperiodeHendelse(
    override val periodeId: UUID,
    override val ident: String,
    val startet: LocalDateTime,
    val avsluttet: LocalDateTime,
) : ArbeidssøkerperiodeHendelse(periodeId, ident, startet) {
    override fun behandle(person: Person) {
        person.arbeidssøkerperioder
            .find { it.periodeId == periodeId }
            ?.let { it.avsluttet = avsluttet }
            ?: run { person.leggTilNyArbeidssøkerperiode(this) }

        person
            .vurderNyStatus()
            .takeIf { it != person.status }
            ?.let {
                person.setStatus(it)
                person.frasiArbeidssøkerBekreftelse(periodeId, periodeAvsluttet = true)
            }
    }
}

fun Arbeidssøkerperiode.aktiv(): Boolean = avsluttet == null

val List<Arbeidssøkerperiode>.gjeldende: Arbeidssøkerperiode?
    get() = this.firstOrNull { it.aktiv() }

fun Person.leggTilNyArbeidssøkerperiode(hendelse: StartetArbeidssøkerperiodeHendelse) {
    arbeidssøkerperioder.add(
        Arbeidssøkerperiode(
            hendelse.periodeId,
            ident,
            hendelse.startet,
            null,
            overtattBekreftelse = null,
        ),
    )
}

fun Person.leggTilNyArbeidssøkerperiode(hendelse: AvsluttetArbeidssøkerperiodeHendelse) {
    arbeidssøkerperioder.add(
        Arbeidssøkerperiode(
            hendelse.periodeId,
            ident,
            hendelse.startet,
            hendelse.avsluttet,
            overtattBekreftelse = false,
        ),
    )
}
