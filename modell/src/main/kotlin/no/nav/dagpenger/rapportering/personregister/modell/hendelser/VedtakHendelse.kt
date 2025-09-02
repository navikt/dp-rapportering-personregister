package no.nav.dagpenger.rapportering.personregister.modell.hendelser

import no.nav.dagpenger.rapportering.personregister.modell.AnsvarligSystem
import no.nav.dagpenger.rapportering.personregister.modell.Kildesystem
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.sendStartMeldingTilMeldekortregister
import java.time.LocalDateTime

enum class VedtakStatus {
    Innvilget,
    Avslått,
    Stanset,
    Avsluttet,
}

data class VedtakHendelse(
    override val ident: String,
    override val dato: LocalDateTime,
    override val startDato: LocalDateTime,
    override val referanseId: String,
    val søknadId: String,
    val status: VedtakStatus,
) : Hendelse {
    override val kilde: Kildesystem = Kildesystem.PJ

    override fun behandle(person: Person) {
        person.hendelser.add(this)
        // TODO: person.setAnsvarligSystem(AnsvarligSystem.DP) når vi har dp-meldekortregister
        person.setAnsvarligSystem(AnsvarligSystem.ARENA)

        person.hendelser
            .filterIsInstance<SøknadHendelse>()
            .find { søknadHendelse -> søknadHendelse.referanseId == søknadId }
            ?.dato
            ?.let {
                person.sendStartMeldingTilMeldekortregister(it)
                // TODO: Må vi gjøre noe annet her? Sette status til DAGPENGERBRUKER? erArbeidssøker = true? meldeplikt = true? meldegruppe = "DAGP"?
            }
    }
}
