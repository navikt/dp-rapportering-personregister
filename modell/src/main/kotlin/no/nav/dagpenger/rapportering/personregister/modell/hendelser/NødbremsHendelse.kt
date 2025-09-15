package no.nav.dagpenger.rapportering.personregister.modell.hendelser

import no.nav.dagpenger.rapportering.personregister.modell.AnsvarligSystem
import no.nav.dagpenger.rapportering.personregister.modell.Kildesystem
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.VedtakType
import no.nav.dagpenger.rapportering.personregister.modell.sendFrasigelsesmelding
import no.nav.dagpenger.rapportering.personregister.modell.sendStoppMeldingTilMeldekortregister
import java.time.LocalDateTime

data class NødbremsHendelse(
    override val ident: String,
    override val dato: LocalDateTime,
    override val startDato: LocalDateTime,
    override val referanseId: String,
    override val kilde: Kildesystem = Kildesystem.Dagpenger,
) : Hendelse {
    override fun behandle(person: Person) {
        person.hendelser.add(this)
        person.setAnsvarligSystem(AnsvarligSystem.ARENA)
        person.setVedtak(VedtakType.INGEN)

        person.sendStoppMeldingTilMeldekortregister(stoppDato = startDato)
    }
}
