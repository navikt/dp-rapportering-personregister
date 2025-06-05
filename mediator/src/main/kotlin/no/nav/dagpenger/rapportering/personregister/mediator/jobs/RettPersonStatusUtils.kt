package no.nav.dagpenger.rapportering.personregister.mediator.jobs

import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.modell.AnnenMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.AvsluttetArbeidssøkerperiodeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.DagpengerMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.MeldepliktHendelse
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.PersonSynkroniseringHendelse
import no.nav.dagpenger.rapportering.personregister.modell.StartetArbeidssøkerperiodeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.Status
import no.nav.dagpenger.rapportering.personregister.modell.SøknadHendelse
import no.nav.dagpenger.rapportering.personregister.modell.erArbeidssøker

private val sikkerLogg = KotlinLogging.logger("tjenestekall")

private fun oppfyllerKravVedSynkronisering(person: Person): Boolean {
    if (person.harKunPersonSynkroniseringHendelse() ||
        person.harKunPersonSynkroniseringOgDAGPHendelse() ||
        person.harKunPersonSynkroniseringOgMeldepliktHendelse()
    ) {
        sikkerLogg.info { "Person med ident ${person.ident} oppfyller krav ved synkronisering. ${person.hendelser}" }
        return true
    }

    return false
}

fun beregnMeldepliktStatus(person: Person) =
    person.hendelser
        .filterIsInstance<MeldepliktHendelse>()
        .sortedWith { a, b ->
            when {
                a.startDato != b.startDato -> b.startDato.compareTo(a.startDato)
                else -> b.dato.compareTo(a.dato)
            }
        }.firstOrNull()
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

fun beregnStatus(person: Person): Status {
    if (!person.erArbeidssøker) {
        return Status.IKKE_DAGPENGERBRUKER
    }

    if (oppfyllerKravVedSynkronisering(person)) {
        return Status.DAGPENGERBRUKER
    }

    val beregnetMeldeplikt = beregnMeldepliktStatus(person)

    val beregnetMeldegruppe = beregnMeldegruppeStatus(person)

    val oppfyllerKrav = beregnetMeldeplikt && beregnetMeldegruppe == "DAGP" && person.erArbeidssøker
    val nyStatus = if (oppfyllerKrav) Status.DAGPENGERBRUKER else Status.IKKE_DAGPENGERBRUKER

    return nyStatus
}

private fun Person.harKunPersonSynkroniseringHendelse(): Boolean =
    hendelser
        .filterNot { it is StartetArbeidssøkerperiodeHendelse || it is AvsluttetArbeidssøkerperiodeHendelse || it is SøknadHendelse }
        .takeIf { it.isNotEmpty() }
        ?.all { it is PersonSynkroniseringHendelse }
        ?: false

private fun Person.harKunPersonSynkroniseringOgDAGPHendelse(): Boolean =
    hendelser
        .filterNot { it is StartetArbeidssøkerperiodeHendelse || it is AvsluttetArbeidssøkerperiodeHendelse || it is SøknadHendelse }
        .takeIf { it.isNotEmpty() }
        ?.all { it is PersonSynkroniseringHendelse || it is DagpengerMeldegruppeHendelse }
        ?: false

private fun Person.harKunPersonSynkroniseringOgMeldepliktHendelse(): Boolean =
    hendelser
        .filterNot { it is StartetArbeidssøkerperiodeHendelse || it is AvsluttetArbeidssøkerperiodeHendelse || it is SøknadHendelse }
        .takeIf { it.isNotEmpty() }
        ?.all { it is PersonSynkroniseringHendelse || it is MeldepliktHendelse }
        ?: false
