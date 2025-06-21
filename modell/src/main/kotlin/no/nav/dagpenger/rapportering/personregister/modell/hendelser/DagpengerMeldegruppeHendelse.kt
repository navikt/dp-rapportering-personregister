package no.nav.dagpenger.rapportering.personregister.modell.hendelser

import no.nav.dagpenger.rapportering.personregister.modell.Kildesystem
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.oppfyllerKrav
import no.nav.dagpenger.rapportering.personregister.modell.sendOvertakelsesmelding
import no.nav.dagpenger.rapportering.personregister.modell.vurderNyStatus
import java.time.LocalDateTime

data class DagpengerMeldegruppeHendelse(
    override val ident: String,
    override val dato: LocalDateTime,
    override val referanseId: String,
    override val startDato: LocalDateTime,
    val sluttDato: LocalDateTime?,
    val meldegruppeKode: String,
    val harMeldtSeg: Boolean,
    override val kilde: Kildesystem = Kildesystem.Arena,
) : Hendelse {
    override fun behandle(person: Person) {
        person.setMeldegruppe(meldegruppeKode)

        person
            .vurderNyStatus()
            .takeIf { it != person.status }
            .takeIf { person.oppfyllerKrav }
            ?.let {
                person.setStatus(it)
                person.sendOvertakelsesmelding()
            }
    }
}
