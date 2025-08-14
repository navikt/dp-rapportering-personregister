package no.nav.dagpenger.rapportering.personregister.modell.hendelser

import no.nav.dagpenger.rapportering.personregister.modell.Kildesystem
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.gjeldende
import no.nav.dagpenger.rapportering.personregister.modell.sendFrasigelsesmelding
import java.time.LocalDateTime

class MeldesyklusErPassertHendelse(
    override val ident: String,
    override val dato: LocalDateTime,
    override val referanseId: String,
    val meldekortregisterPeriodeId: String,
    val periodeFraOgMed: LocalDateTime,
    val periodeTilOgMed: LocalDateTime,
) : Hendelse {
    override val startDato by lazy { periodeFraOgMed }

    override fun behandle(person: Person) {
        person.hendelser.add(this)

        person.arbeidssøkerperioder.gjeldende
            ?.also { periode ->
                person.sendFrasigelsesmelding(periode.periodeId, true)
            }
    }

    override val kilde: Kildesystem = Kildesystem.Meldekortregister
}
