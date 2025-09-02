package no.nav.dagpenger.rapportering.personregister.modell.hendelser

import no.nav.dagpenger.rapportering.personregister.modell.AnsvarligSystem
import no.nav.dagpenger.rapportering.personregister.modell.Kildesystem
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.sendStartMeldingTilMeldekortregister
import java.time.LocalDateTime

data class VedtakHendelse(
    override val ident: String,
    override val dato: LocalDateTime,
    override val startDato: LocalDateTime,
    override val referanseId: String,
    val søknadId: String,
) : Hendelse {
    override val kilde: Kildesystem = Kildesystem.PJ

    override fun behandle(person: Person) {
        person.hendelser.add(this)
        // TODO: person.setAnsvarligSystem(AnsvarligSystem.DP) når vi har dp-meldekortregister
        person.setAnsvarligSystem(AnsvarligSystem.ARENA)

        person.hendelser
            .filter { it is SøknadHendelse }
            .find { it.referanseId == søknadId }
            ?.dato
            ?.let {
                person.sendStartMeldingTilMeldekortregister(it)
                // TODO: Må vi gjøre noe annet her? Sette status til DAGPENGERBRUKER? erArbeidssøker = true? meldeplikt = true? meldegruppe = "DAGP"?
            }
    }
}
