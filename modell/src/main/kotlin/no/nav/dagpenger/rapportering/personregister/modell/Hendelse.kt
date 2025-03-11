package no.nav.dagpenger.rapportering.personregister.modell

import no.nav.dagpenger.rapportering.personregister.modell.Kildesystem.Arena
import java.time.LocalDateTime

interface Hendelse {
    val ident: String
    val dato: LocalDateTime
    val kilde: Kildesystem
    val referanseId: String

    fun behandle(person: Person)
}

data class SøknadHendelse(
    override val ident: String,
    override val dato: LocalDateTime,
    override val referanseId: String,
) : Hendelse {
    override val kilde: Kildesystem = Kildesystem.Søknad

    override fun behandle(person: Person) {
        person
            .vurderNyStatus()
            .takeIf { it != person.status }
            .takeIf { person.oppfyllerKrav }
            ?.let {
                person.setStatus(it)
                person.overtaArbeidssøkerBekreftelse()
            }
    }
}

data class DagpengerMeldegruppeHendelse(
    override val ident: String,
    override val dato: LocalDateTime,
    override val referanseId: String,
    val startDato: LocalDateTime,
    val sluttDato: LocalDateTime?,
    val meldegruppeKode: String,
    override val kilde: Kildesystem = Arena,
) : Hendelse {
    override fun behandle(person: Person) {
        person.meldegruppe = meldegruppeKode

        person
            .vurderNyStatus()
            .takeIf { it != person.status }
            .takeIf { person.oppfyllerKrav }
            ?.let {
                person.setStatus(it)
                person.overtaArbeidssøkerBekreftelse()
            }
    }
}

data class AnnenMeldegruppeHendelse(
    override val ident: String,
    override val dato: LocalDateTime,
    override val referanseId: String,
    val startDato: LocalDateTime,
    val sluttDato: LocalDateTime?,
    val meldegruppeKode: String,
) : Hendelse {
    override val kilde = Kildesystem.Arena

    override fun behandle(person: Person) {
        person.meldegruppe = meldegruppeKode

        person
            .vurderNyStatus()
            .takeIf { it != person.status }
            .takeIf { !person.oppfyllerKrav }
            ?.let {
                person.setStatus(it)
                person.arbeidssøkerperioder.gjeldende
                    ?.let { periode -> person.frasiArbeidssøkerBekreftelse(periode.periodeId) }
            }
    }
}

data class MeldepliktHendelse(
    override val ident: String,
    override val dato: LocalDateTime,
    override val referanseId: String,
    val startDato: LocalDateTime,
    val sluttDato: LocalDateTime?,
    val statusMeldeplikt: Boolean,
    override val kilde: Kildesystem = Arena,
) : Hendelse {
    override fun behandle(person: Person) {
        person.meldeplikt = statusMeldeplikt

        person
            .vurderNyStatus()
            .takeIf { it != person.status }
            ?.let {
                person.setStatus(it)
                if (person.oppfyllerKrav) {
                    person.overtaArbeidssøkerBekreftelse()
                } else {
                    person.arbeidssøkerperioder.gjeldende
                        ?.let { periode -> person.frasiArbeidssøkerBekreftelse(periode.periodeId) }
                }
            }
    }
}

data class PersonSynkroniseringHendelse(
    override val ident: String,
    override val dato: LocalDateTime,
    override val referanseId: String,
) : Hendelse {
    override val kilde = Kildesystem.Dagpenger

    override fun behandle(person: Person) {
        person.meldeplikt = true
        person.meldegruppe = "DAGP"
    }
}

enum class Kildesystem {
    Søknad,
    Arena,
    Arbeidssokerregisteret,
    Dagpenger,
}
