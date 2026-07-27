package no.nav.dagpenger.rapportering.personregister.modell.hendelser

import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.utils.erIFortid

interface MeldegruppeHendelse : Hendelse

fun MeldegruppeHendelse.gjelderTilbakeITid(person: Person) =
    person.hendelser
        .filterIsInstance<MeldegruppeHendelse>()
        .maxByOrNull { it.startDato }
        ?.let { sisteMeldegruppeHendelse ->
            this.startDato.isBefore(sisteMeldegruppeHendelse.startDato) &&
                this.sluttDato?.erIFortid() == true
        } ?: false
