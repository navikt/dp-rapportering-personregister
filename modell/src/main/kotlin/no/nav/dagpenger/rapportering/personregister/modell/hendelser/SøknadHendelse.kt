package no.nav.dagpenger.rapportering.personregister.modell.hendelser

import no.nav.dagpenger.rapportering.personregister.modell.Kildesystem
import no.nav.dagpenger.rapportering.personregister.modell.Person
import java.time.LocalDateTime

data class SøknadHendelse(
    override val ident: String,
    override val dato: LocalDateTime,
    override val startDato: LocalDateTime,
    override val referanseId: String,
) : Hendelse {
    override val kilde: Kildesystem = Kildesystem.Søknad

    override fun behandle(person: Person) {
        // Forretningslogikk er flyttet til SøknadService
    }
}
