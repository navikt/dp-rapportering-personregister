package no.nav.dagpenger.rapportering.personregister.modell.hendelser

import no.nav.dagpenger.rapportering.personregister.modell.Kildesystem
import no.nav.dagpenger.rapportering.personregister.modell.Person
import java.time.LocalDateTime

interface Hendelse {
    val ident: String
    val dato: LocalDateTime
    val startDato: LocalDateTime
    val kilde: Kildesystem
    val referanseId: String

    fun behandle(person: Person)
}
