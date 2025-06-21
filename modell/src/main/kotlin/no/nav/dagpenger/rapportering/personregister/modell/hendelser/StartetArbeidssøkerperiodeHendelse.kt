package no.nav.dagpenger.rapportering.personregister.modell.hendelser

import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.leggTilNyArbeidssøkerperiode
import no.nav.dagpenger.rapportering.personregister.modell.overtattBekreftelse
import no.nav.dagpenger.rapportering.personregister.modell.sendOvertakelsesmelding
import no.nav.dagpenger.rapportering.personregister.modell.vurderNyStatus
import java.time.LocalDateTime
import java.util.UUID

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

        person
            .vurderNyStatus()
            .takeIf { it != person.status }
            ?.also { person.setStatus(it) }
            ?.takeIf { !person.overtattBekreftelse }
            ?.also { person.sendOvertakelsesmelding() }
    }
}
