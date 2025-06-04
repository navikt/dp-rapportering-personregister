package no.nav.dagpenger.rapportering.personregister.mediator.jobs

import no.nav.dagpenger.rapportering.personregister.modell.AnnenMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.Arbeidssøkerperiode
import no.nav.dagpenger.rapportering.personregister.modell.AvsluttetArbeidssøkerperiodeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.DagpengerMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.MeldepliktHendelse
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.PersonSynkroniseringHendelse
import no.nav.dagpenger.rapportering.personregister.modell.StartetArbeidssøkerperiodeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.Status
import no.nav.dagpenger.rapportering.personregister.modell.erArbeidssøker

fun beregnMeldepliktStatus(person: Person) =
    person.hendelser
        .filterIsInstance<MeldepliktHendelse>()
        .maxByOrNull { it.startDato }
        ?.statusMeldeplikt
        ?: false

fun beregnMeldegruppeStatus(person: Person) =
    person.hendelser
        .filter { it is DagpengerMeldegruppeHendelse || it is AnnenMeldegruppeHendelse }
        .sortedWith { a, b ->
            when {
                a.startDato != b.startDato -> b.startDato.compareTo(a.startDato)
                else -> b.dato.compareTo(a.dato)
            }
        }.firstOrNull()
        ?.let {
            when (it) {
                is DagpengerMeldegruppeHendelse -> it.meldegruppeKode
                is AnnenMeldegruppeHendelse -> it.meldegruppeKode
                else -> null
            }
        }

fun oppfyllerkravVedSynkronisering(person: Person): Boolean {
    val sisteMeldeplikt = beregnMeldepliktStatus(person)
    val sisteMelgruppe = beregnMeldegruppeStatus(person)

    return person.hendelser
        .takeIf { hendelser -> hendelser.any { it is PersonSynkroniseringHendelse } }
        ?.filter {
            it is DagpengerMeldegruppeHendelse ||
                it is AnnenMeldegruppeHendelse ||
                it is PersonSynkroniseringHendelse ||
                it is MeldepliktHendelse
        }?.maxByOrNull { it.startDato }
        ?.let {
            when (it) {
                is PersonSynkroniseringHendelse -> return true
                is DagpengerMeldegruppeHendelse -> return sisteMeldeplikt
                is AnnenMeldegruppeHendelse -> return false
                is MeldepliktHendelse -> it.statusMeldeplikt && sisteMelgruppe == "DAGP"
                else -> false
            }
        } ?: false
}

fun harKunPersonSynkroniseringOgDAGPHendelse(person: Person): Boolean =
    person.hendelser
        .filterNot { it is StartetArbeidssøkerperiodeHendelse || it is AvsluttetArbeidssøkerperiodeHendelse }
        .takeIf { it.isNotEmpty() }
        ?.all { it is PersonSynkroniseringHendelse || it is DagpengerMeldegruppeHendelse }
        ?: false

fun beregnStatus(person: Person): Status {
    if (oppfyllerkravVedSynkronisering(person)) {
        return Status.DAGPENGERBRUKER
    } else if (harKunPersonSynkroniseringOgDAGPHendelse(person)) {
        return Status.DAGPENGERBRUKER
    } else {
        val beregnetMeldeplikt = beregnMeldepliktStatus(person)

        val beregnetMeldegruppe = beregnMeldegruppeStatus(person)

        val oppfyllerKrav = beregnetMeldeplikt && beregnetMeldegruppe == "DAGP" && person.erArbeidssøker
        val nyStatus = if (oppfyllerKrav) Status.DAGPENGERBRUKER else Status.IKKE_DAGPENGERBRUKER

        return nyStatus
    }
}

fun rettPersonStatus(
    person: Person,
    sisteArbeidssøkerperiode: Arbeidssøkerperiode?,
): Person {
    if (oppfyllerkravVedSynkronisering(person)) {
        person.setMeldeplikt(true)
        person.setMeldegruppe("DAGP")

        if (person.status != Status.DAGPENGERBRUKER) {
            person.setStatus(Status.DAGPENGERBRUKER)
        }
    } else if (harKunPersonSynkroniseringOgDAGPHendelse(person)) {
        person.setMeldeplikt(true)
        person.setMeldegruppe("DAGP")

        if (person.status != Status.DAGPENGERBRUKER) {
            person.setStatus(Status.DAGPENGERBRUKER)
        }
    } else {
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
    }

    return person
}
