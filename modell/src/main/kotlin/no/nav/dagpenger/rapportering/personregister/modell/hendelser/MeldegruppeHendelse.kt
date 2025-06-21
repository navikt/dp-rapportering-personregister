package no.nav.dagpenger.rapportering.personregister.modell.hendelser

import no.nav.dagpenger.rapportering.personregister.modell.Person

interface MeldegruppeHendelse : Hendelse

fun MeldegruppeHendelse.gjelderTilbakeITid(person: Person) =
    person.hendelser
        .filterIsInstance<MeldegruppeHendelse>()
        .maxByOrNull { it.startDato }
        ?.let { sisteMeldegruppeHendelse ->
            !this.startDato.isAfter(sisteMeldegruppeHendelse.startDato)
        } ?: false
