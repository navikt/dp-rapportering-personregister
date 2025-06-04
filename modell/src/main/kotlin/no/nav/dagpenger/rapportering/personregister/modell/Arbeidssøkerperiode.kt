package no.nav.dagpenger.rapportering.personregister.modell

import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.modell.Status.DAGPENGERBRUKER
import no.nav.dagpenger.rapportering.personregister.modell.Status.IKKE_DAGPENGERBRUKER
import java.time.LocalDateTime
import java.util.UUID

private val logger = KotlinLogging.logger {}

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
    override val startDato: LocalDateTime,
) : Hendelse {
    override val referanseId by lazy { periodeId.toString() }

    override fun behandle(person: Person) {}

    override val kilde: Kildesystem = Kildesystem.Arbeidssokerregisteret
}

data class StartetArbeidssøkerperiodeHendelse(
    override val periodeId: UUID,
    override val ident: String,
    val startet: LocalDateTime,
) : ArbeidssøkerperiodeHendelse(periodeId, ident, LocalDateTime.now(), startet) {
    override fun behandle(person: Person) {
        person.arbeidssøkerperioder
            .none { it.periodeId == periodeId }
            .takeIf { it }
            ?.let { person.leggTilNyArbeidssøkerperiode(this) }

        logger.info {
            "Startet arbeidssøkerperiode for person med meldepliktstatus: ${person.meldeplikt} og meldegruppe: ${person.meldegruppe}"
        }

        val oppfyllerKrav = person.meldeplikt && person.meldegruppe == "DAGP"

        if (!oppfyllerKrav) {
            logger.info { "Person oppfyller ikke krav. meldeplikt: ${person.meldeplikt}. meldegruppe: ${person.meldegruppe}." }
            if (person.status != IKKE_DAGPENGERBRUKER) {
                logger.info { "Oppdaterer status til IKKE_DAGPENGERBRUKER" }
                person.setStatus(IKKE_DAGPENGERBRUKER)
            } else {
                logger.info { "Personstatus er allerede IKKE_DAGPENGERBRUKER, ingen endring nødvendig." }
            }
            return
        }

        logger.info { "Oppfyller krav med meldeplikt = ${person.meldeplikt} og meldegruppe =${person.meldegruppe}." }

        if (person.status != DAGPENGERBRUKER) {
            logger.info { "Oppdaterer personstatus til DAGPENGERBRUKER." }
            person.setStatus(DAGPENGERBRUKER)
        } else {
            logger.info { "Personstatus er allerede DAGPENGERBRUKER, ingen endring nødvendig." }
        }

        if (!person.overtattBekreftelse) {
            logger.info { "Setter i gang overtakelse til personen." }
            person.sendOvertakelsesmelding()
        } else {
            logger.info {
                "Arbeidssøkerperiode-overtakelsesbekreftelse er allerede satt til true ."
            }
        }
    }
}

data class AvsluttetArbeidssøkerperiodeHendelse(
    override val periodeId: UUID,
    override val ident: String,
    val startet: LocalDateTime,
    val avsluttet: LocalDateTime,
) : ArbeidssøkerperiodeHendelse(periodeId, ident, LocalDateTime.now(), startet) {
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
                person.merkPeriodeSomIkkeOvertatt(periodeId)
            }
    }
}

fun Arbeidssøkerperiode.aktiv(): Boolean = avsluttet == null

val List<Arbeidssøkerperiode>.gjeldende: Arbeidssøkerperiode?
    get() = this.firstOrNull { it.aktiv() }
