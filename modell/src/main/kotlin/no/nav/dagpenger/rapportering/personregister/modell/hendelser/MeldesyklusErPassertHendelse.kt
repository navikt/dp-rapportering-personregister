package no.nav.dagpenger.rapportering.personregister.modell.hendelser

import no.nav.dagpenger.rapportering.personregister.modell.Kildesystem
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.gjeldende
import no.nav.dagpenger.rapportering.personregister.modell.sendFrasigelsesmelding
import no.nav.dagpenger.rapportering.personregister.modell.ÅrsakTilUtmelding
import java.time.LocalDateTime

class MeldesyklusErPassertHendelse(
    override val ident: String,
    override val dato: LocalDateTime = LocalDateTime.now(),
    override val startDato: LocalDateTime,
    override val referanseId: String,
) : Hendelse {
    override val sluttDato = null

    override fun behandle(person: Person) {
        person.hendelser.add(this)

        person.arbeidssøkerperioder.gjeldende
            ?.also { periode ->
                periode.årsakTilUtmelding = ÅrsakTilUtmelding.IKKE_MELDT_SEG_PÅ_21_DAGER
                person.sendFrasigelsesmelding(periode.periodeId, true)
            }
    }

    override val kilde: Kildesystem = Kildesystem.Meldekortregister
}
