package no.nav.dagpenger.rapportering.personregister.modell.hendelser

import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.leggTilNyArbeidssøkerperiode
import no.nav.dagpenger.rapportering.personregister.modell.merkPeriodeSomIkkeOvertatt
import no.nav.dagpenger.rapportering.personregister.modell.vurderNyStatus
import java.time.LocalDateTime
import java.util.UUID

data class AvsluttetArbeidssøkerperiodeHendelse(
    override val periodeId: UUID,
    override val ident: String,
    val startet: LocalDateTime,
    val avsluttet: LocalDateTime,
) : ArbeidssøkerperiodeHendelse(periodeId, ident, LocalDateTime.now(), startet) {
    override fun behandle(person: Person) {
        person.hendelser.add(this)

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
