package no.nav.dagpenger.rapportering.personregister.modell.hendelser

import no.nav.dagpenger.rapportering.personregister.modell.Kildesystem
import no.nav.dagpenger.rapportering.personregister.modell.Kildesystem.Dagpenger
import no.nav.dagpenger.rapportering.personregister.modell.Person
import java.time.LocalDateTime

data class PersonSynkroniseringHendelse(
    override val ident: String,
    override val dato: LocalDateTime,
    override val referanseId: String,
    override val startDato: LocalDateTime,
) : Hendelse {
    override val kilde: Kildesystem = Dagpenger

    override fun behandle(person: Person) {
        person.hendelser.add(this)
        person.setMeldeplikt(true)
        person.setMeldegruppe("DAGP")
    }
}
