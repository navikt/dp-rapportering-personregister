package no.nav.dagpenger.rapportering.personregister.modell.hendelser

import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.Status
import no.nav.dagpenger.rapportering.personregister.modell.leggTilNyArbeidssøkerperiode
import no.nav.dagpenger.rapportering.personregister.modell.overtattBekreftelse
import no.nav.dagpenger.rapportering.personregister.modell.sendOvertakelsesmelding
import java.time.LocalDateTime
import java.util.UUID

private val logger = mu.KotlinLogging.logger {}

data class StartetArbeidssøkerperiodeHendelse(
    override val periodeId: UUID,
    override val ident: String,
    val startet: LocalDateTime,
) : ArbeidssøkerperiodeHendelse(periodeId, ident, LocalDateTime.now(), startet) {
    override fun behandle(person: Person) {
        person.hendelser.add(this)
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
            if (person.status != Status.IKKE_DAGPENGERBRUKER) {
                logger.info { "Oppdaterer status til IKKE_DAGPENGERBRUKER" }
                person.setStatus(Status.IKKE_DAGPENGERBRUKER)
            } else {
                logger.info { "Personstatus er allerede IKKE_DAGPENGERBRUKER, ingen endring nødvendig." }
            }
            return
        }

        logger.info { "Oppfyller krav med meldeplikt = ${person.meldeplikt} og meldegruppe =${person.meldegruppe}." }

        if (person.status != Status.DAGPENGERBRUKER) {
            logger.info { "Oppdaterer personstatus til DAGPENGERBRUKER." }
            person.setStatus(Status.DAGPENGERBRUKER)
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
