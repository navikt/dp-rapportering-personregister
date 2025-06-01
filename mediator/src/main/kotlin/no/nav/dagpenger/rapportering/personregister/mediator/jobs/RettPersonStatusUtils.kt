package no.nav.dagpenger.rapportering.personregister.mediator.jobs

import no.nav.dagpenger.rapportering.personregister.modell.AnnenMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.Arbeidssøkerperiode
import no.nav.dagpenger.rapportering.personregister.modell.DagpengerMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.MeldepliktHendelse
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.Status

fun beregnMeldepliktStatus(person: Person) =
    person.hendelser
        .filterIsInstance<MeldepliktHendelse>()
        .maxByOrNull { it.dato }
        ?.statusMeldeplikt
        ?: false

fun beregnMeldegruppeStatus(person: Person) =
    person.hendelser
        .filter { it is DagpengerMeldegruppeHendelse || it is AnnenMeldegruppeHendelse }
        .maxByOrNull { it.dato }
        ?.let {
            when (it) {
                is DagpengerMeldegruppeHendelse -> it.meldegruppeKode
                is AnnenMeldegruppeHendelse -> it.meldegruppeKode
                else -> null
            }
        }

fun rettPersonStatus(
    person: Person,
    sisteArbeidssøkerperiode: Arbeidssøkerperiode?,
): Person {
    val beregnetMeldeplikt = beregnMeldepliktStatus(person)
    if (person.meldeplikt != beregnetMeldeplikt) {
        person.setMeldeplikt(beregnetMeldeplikt)
    }

    val beregnetMeldegruppe = beregnMeldegruppeStatus(person)
    if (person.meldegruppe != beregnetMeldegruppe) {
        person.setMeldegruppe(beregnetMeldegruppe)
    }

    val erArbeidssøker = sisteArbeidssøkerperiode?.avsluttet == null

    val oppfyllerKrav = beregnetMeldeplikt && beregnetMeldegruppe == "DAGP" && erArbeidssøker
    val nyStatus = if (oppfyllerKrav) Status.DAGPENGERBRUKER else Status.IKKE_DAGPENGERBRUKER

    if (person.status != nyStatus) {
        person.setStatus(nyStatus)
    }

    return person
}
