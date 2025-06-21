package no.nav.dagpenger.rapportering.personregister.modell.hendelser

import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.gjeldende
import no.nav.dagpenger.rapportering.personregister.modell.leggTilNyArbeidssøkerperiode
import no.nav.dagpenger.rapportering.personregister.modell.overtattBekreftelse
import no.nav.dagpenger.rapportering.personregister.modell.sendOvertakelsesmelding
import no.nav.dagpenger.rapportering.personregister.modell.vurderNyStatus
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

        oppdaterGjeldendeperiode(person, this)

        person
            .vurderNyStatus()
            .takeIf { it != person.status }
            ?.also { person.setStatus(it) }
            ?.takeIf { !person.overtattBekreftelse }
            ?.also { person.sendOvertakelsesmelding() }
    }
}

private fun oppdaterGjeldendeperiode(
    person: Person,
    hendelse: StartetArbeidssøkerperiodeHendelse,
) {
    if (person.arbeidssøkerperioder.none { it.periodeId == hendelse.periodeId }) {
        person.arbeidssøkerperioder.gjeldende?.apply {
            logger.warn { "Personen har allerede en aktiv arbeidssøkerperiode. Avslutter den før vi starter en ny." }
            avsluttet = LocalDateTime.now()
            overtattBekreftelse = false
        }

        person.leggTilNyArbeidssøkerperiode(hendelse)
    }
}
