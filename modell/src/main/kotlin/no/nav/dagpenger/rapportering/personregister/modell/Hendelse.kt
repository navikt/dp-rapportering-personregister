package no.nav.dagpenger.rapportering.personregister.modell

import no.nav.dagpenger.rapportering.personregister.modell.Kildesystem.Dagpenger
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.Hendelse
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

data class VedtakHendelse(
    override val ident: String,
    override val dato: LocalDateTime,
    override val startDato: LocalDateTime,
    override val referanseId: String,
) : Hendelse {
    override val kilde: Kildesystem = Kildesystem.PJ

    override fun behandle(person: Person) {
        person.hendelser.add(this)
        person.setAnsvarligSystem(AnsvarligSystem.DP)
        person.sendStartMeldingTilMeldekortregister(startDato)
        // TODO: Må vi gjøre noe annet her? Sette status til DAGPENGERBRUKER? erArbeidssøker = true? meldeplikt = true? meldegruppe = "DAGP"?
    }
}

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

data class PersonIkkeDagpengerSynkroniseringHendelse(
    override val ident: String,
    override val dato: LocalDateTime,
    override val referanseId: String,
    override val startDato: LocalDateTime,
    val dagpengerbruker: Boolean = true,
) : Hendelse {
    override val kilde: Kildesystem = Dagpenger

    override fun behandle(person: Person) {
        person.hendelser.add(this)
        person.setMeldeplikt(false)
        person.setMeldegruppe(null)
    }
}
