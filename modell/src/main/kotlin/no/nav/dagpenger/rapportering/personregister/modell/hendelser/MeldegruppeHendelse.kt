package no.nav.dagpenger.rapportering.personregister.modell.hendelser

import no.nav.dagpenger.rapportering.personregister.modell.Person
import java.time.LocalDateTime
import java.time.LocalDateTime.now

interface MeldegruppeHendelse : Hendelse {
    val sluttDato: LocalDateTime?
}

fun MeldegruppeHendelse.gjelderTilbakeITid(person: Person) =
    person.hendelser
        .filterIsInstance<MeldegruppeHendelse>()
        .maxByOrNull { it.startDato }
        ?.let { sisteMeldegruppeHendelse ->
            this.startDato.isBefore(sisteMeldegruppeHendelse.startDato) &&
                this.sluttDato?.isBefore(now()) == true
        } ?: false
