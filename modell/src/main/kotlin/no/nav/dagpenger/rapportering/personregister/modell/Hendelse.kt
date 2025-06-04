package no.nav.dagpenger.rapportering.personregister.modell

import no.nav.dagpenger.rapportering.personregister.modell.Kildesystem.Arena
import no.nav.dagpenger.rapportering.personregister.modell.Kildesystem.Dagpenger
import java.time.LocalDateTime

interface Hendelse {
    val ident: String
    val dato: LocalDateTime
    val startDato: LocalDateTime
    val kilde: Kildesystem
    val referanseId: String

    fun behandle(person: Person)
}

data class SøknadHendelse(
    override val ident: String,
    override val dato: LocalDateTime,
    override val startDato: LocalDateTime,
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
                person.sendOvertakelsesmelding()
            }
    }
}

data class DagpengerMeldegruppeHendelse(
    override val ident: String,
    override val dato: LocalDateTime,
    override val referanseId: String,
    override val startDato: LocalDateTime,
    val sluttDato: LocalDateTime?,
    val meldegruppeKode: String,
    val harMeldtSeg: Boolean,
    override val kilde: Kildesystem = Arena,
) : Hendelse {
    override fun behandle(person: Person) {
        person.setMeldegruppe(meldegruppeKode)

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

data class AnnenMeldegruppeHendelse(
    override val ident: String,
    override val dato: LocalDateTime,
    override val referanseId: String,
    override val startDato: LocalDateTime,
    val sluttDato: LocalDateTime?,
    val meldegruppeKode: String,
    val harMeldtSeg: Boolean,
) : Hendelse {
    override val kilde: Kildesystem = Arena

    override fun behandle(person: Person) {
        person.setMeldegruppe(meldegruppeKode)

        person
            .vurderNyStatus()
            .takeIf { it != person.status }
            .takeIf { !person.oppfyllerKrav }
            ?.let {
                person.setStatus(it)
                person.arbeidssøkerperioder.gjeldende
                    ?.let { periode -> person.sendFrasigelsesmelding(periode.periodeId, !harMeldtSeg) }
            }
    }
}

data class MeldepliktHendelse(
    override val ident: String,
    override val dato: LocalDateTime,
    override val referanseId: String,
    override val startDato: LocalDateTime,
    val sluttDato: LocalDateTime?,
    val statusMeldeplikt: Boolean,
    val harMeldtSeg: Boolean,
    override val kilde: Kildesystem = Arena,
) : Hendelse {
    override fun behandle(person: Person) {
        person.setMeldeplikt(statusMeldeplikt)

        person
            .vurderNyStatus()
            .takeIf { it != person.status }
            ?.let {
                person.setStatus(it)
                if (person.oppfyllerKrav) {
                    person.sendOvertakelsesmelding()
                } else {
                    person.arbeidssøkerperioder.gjeldende
                        ?.let { periode -> person.sendFrasigelsesmelding(periode.periodeId, !harMeldtSeg) }
                }
            }
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
        person.setMeldeplikt(true)
        person.setMeldegruppe("DAGP")
    }
}

enum class Kildesystem {
    Søknad,
    Arena,
    Arbeidssokerregisteret,
    Dagpenger,
}
