package no.nav.dagpenger.rapportering.personregister.modell.hendelser

import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.leggTilNyArbeidssøkerperiode
import no.nav.dagpenger.rapportering.personregister.modell.merkPeriodeSomIkkeOvertatt
import no.nav.dagpenger.rapportering.personregister.modell.vurderNyStatus
import java.time.LocalDateTime
import java.time.LocalDateTime.now
import java.util.UUID

data class AvsluttetArbeidssøkerperiodeHendelse(
    override val periodeId: UUID,
    override val ident: String,
    override val startDato: LocalDateTime,
    override val sluttDato: LocalDateTime,
    override val dato: LocalDateTime = now(),
) : ArbeidssøkerperiodeHendelse(periodeId) {
    override fun behandle(person: Person) {
        person.hendelser.add(this)

        person.arbeidssøkerperioder
            .find { it.periodeId == periodeId }
            ?.let { it.avsluttet = sluttDato }
            ?: run { person.leggTilNyArbeidssøkerperiode(this) }

        person
            .vurderNyStatus()
            .takeIf { it != person.status }
            ?.let {
                person.setStatus(it)
            }
        // Perioden er avsluttet, så den må markeres som ikke overtatt
        person.merkPeriodeSomIkkeOvertatt(periodeId)
    }
}
