package no.nav.dagpenger.rapportering.personregister.modell.hendelser

import no.nav.dagpenger.rapportering.personregister.modell.Kildesystem
import no.nav.dagpenger.rapportering.personregister.modell.Person
import java.time.LocalDateTime
import java.util.UUID

abstract class ArbeidssøkerperiodeHendelse(
    open val periodeId: UUID,
) : Hendelse {
    override val referanseId by lazy { periodeId.toString() }

    override fun behandle(person: Person) {}

    override val kilde: Kildesystem = Kildesystem.Arbeidssokerregisteret
}
