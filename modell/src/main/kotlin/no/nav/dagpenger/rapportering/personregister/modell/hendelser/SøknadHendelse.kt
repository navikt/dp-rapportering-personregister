package no.nav.dagpenger.rapportering.personregister.modell.hendelser

import no.nav.dagpenger.rapportering.personregister.modell.Kildesystem
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.oppfyllerKrav
import no.nav.dagpenger.rapportering.personregister.modell.sendOvertakelsesmelding
import no.nav.dagpenger.rapportering.personregister.modell.vurderNyStatus
import java.time.LocalDateTime

data class SøknadHendelse(
    override val ident: String,
    override val dato: LocalDateTime,
    override val startDato: LocalDateTime,
    override val referanseId: String,
) : Hendelse {
    override val kilde: Kildesystem = Kildesystem.Søknad

    override fun behandle(person: Person) {
        person.hendelser.add(this)
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
