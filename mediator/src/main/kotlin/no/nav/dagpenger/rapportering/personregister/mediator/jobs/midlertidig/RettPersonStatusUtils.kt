package no.nav.dagpenger.rapportering.personregister.mediator.jobs.midlertidig

import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.PersonSynkroniseringHendelse
import no.nav.dagpenger.rapportering.personregister.modell.Status
import no.nav.dagpenger.rapportering.personregister.modell.SøknadHendelse
import no.nav.dagpenger.rapportering.personregister.modell.erArbeidssøker
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.AnnenMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.AvsluttetArbeidssøkerperiodeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.DagpengerMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.MeldepliktHendelse
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.StartetArbeidssøkerperiodeHendelse

private val sikkerLogg = KotlinLogging.logger("tjenestekall")

private fun oppfyllerKravVedSynkronisering(person: Person): Boolean {
    if (person.harKunPersonSynkroniseringHendelse() ||
        person.harPersonsynkroniseringSomSisteHendelse() ||
        person.harKunPersonSynkroniseringOgDAGPHendelse() ||
        person.harKunPersonSynkroniseringOgMeldepliktHendelse() ||
        person.oppfyllerVedKunMeldegruppeOgPersonsynkroniseringHendelse()
    ) {
        sikkerLogg.info { "Person med ident ${person.ident} oppfyller krav ved synkronisering. ${person.hendelser}" }
        return true
    }
    return false
}

fun beregnMeldepliktStatus(person: Person) =
    person.hendelser
        .filter { it is MeldepliktHendelse || it is PersonSynkroniseringHendelse }
        .sortedWith { a, b ->
            when {
                a.startDato != b.startDato -> b.startDato.compareTo(a.startDato)
                else -> b.dato.compareTo(a.dato)
            }
        }.firstOrNull()
        ?.let {
            when (it) {
                is MeldepliktHendelse -> it.statusMeldeplikt
                is PersonSynkroniseringHendelse -> true
                else -> false
            }
        } ?: false

fun beregnMeldegruppeStatus(person: Person) =
    person.hendelser
        .filter { it is DagpengerMeldegruppeHendelse || it is AnnenMeldegruppeHendelse || it is PersonSynkroniseringHendelse }
        .sortedWith { a, b ->
            when {
                a.startDato != b.startDato -> b.startDato.compareTo(a.startDato)
                else -> b.dato.compareTo(a.dato)
            }
        }.firstOrNull()
        ?.let {
            when (it) {
                is PersonSynkroniseringHendelse -> "DAGP"
                is DagpengerMeldegruppeHendelse -> it.meldegruppeKode
                is AnnenMeldegruppeHendelse -> it.meldegruppeKode
                else -> null
            }
        }

fun beregnStatus(person: Person): Status {
    if (!person.erArbeidssøker) {
        sikkerLogg.info { "Person med ident ${person.ident} er ikke arbeidssøker, setter status til IKKE_DAGPENGERBRUKER" }
        return Status.IKKE_DAGPENGERBRUKER
    }

    if (oppfyllerKravVedSynkronisering(person)) {
        sikkerLogg.info { "Person med ident ${person.ident} oppfyller krav ved synkronisering, setter status til DAGPENGERBRUKER" }
        return Status.DAGPENGERBRUKER
    }

    val beregnetMeldeplikt = beregnMeldepliktStatus(person)

    val beregnetMeldegruppe = beregnMeldegruppeStatus(person)

    sikkerLogg.info {
        "Person med ident ${person.ident} har beregnet meldeplikt: $beregnetMeldeplikt, " +
            "beregnet meldegruppe: $beregnetMeldegruppe, arbeidssøker: ${person.erArbeidssøker}"
    }

    val oppfyllerKrav = beregnetMeldeplikt && beregnetMeldegruppe == "DAGP" && person.erArbeidssøker
    val nyStatus = if (oppfyllerKrav) Status.DAGPENGERBRUKER else Status.IKKE_DAGPENGERBRUKER

    return nyStatus
}

fun rettAvvik(
    person: Person,
    nyStatus: Status,
) {
    if (nyStatus == Status.DAGPENGERBRUKER) {
        person.setMeldeplikt(true)
        person.setMeldegruppe("DAGP")
        person.setStatus(Status.DAGPENGERBRUKER)
        person.observers.forEach { it.sendOvertakelsesmelding(person) }
    } else {
        person.setStatus(Status.IKKE_DAGPENGERBRUKER)
        person.observers.forEach { it.sendFrasigelsesmelding(person) }
    }
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

private fun Person.harPersonsynkroniseringSomSisteHendelse(): Boolean =
    hendelser
        .filterNot { it is StartetArbeidssøkerperiodeHendelse || it is AvsluttetArbeidssøkerperiodeHendelse || it is SøknadHendelse }
        .takeIf { it.isNotEmpty() }
        ?.sortedWith { a, b ->
            when {
                a.startDato != b.startDato -> b.startDato.compareTo(a.startDato)
                else -> b.dato.compareTo(a.dato)
            }
        }?.firstOrNull()
        ?.let { it is PersonSynkroniseringHendelse }
        ?: false

private fun Person.harKunMeldegruppeOgPersonsynkroniseringHendelse(): Boolean =
    hendelser
        .filterNot { it is StartetArbeidssøkerperiodeHendelse || it is AvsluttetArbeidssøkerperiodeHendelse || it is SøknadHendelse }
        .takeIf { it.isNotEmpty() }
        ?.all { it is PersonSynkroniseringHendelse || it is DagpengerMeldegruppeHendelse || it is AnnenMeldegruppeHendelse }
        ?: false

private fun Person.oppfyllerVedKunMeldegruppeOgPersonsynkroniseringHendelse(): Boolean {
    if (harKunMeldegruppeOgPersonsynkroniseringHendelse()) {
        sikkerLogg.info { "Person med ident $ident oppfyller krav ved synkronisering. $hendelser" }

        val sisteHendelse =
            hendelser
                .filterNot {
                    it is StartetArbeidssøkerperiodeHendelse ||
                        it is AvsluttetArbeidssøkerperiodeHendelse ||
                        it is SøknadHendelse
                }.sortedWith { a, b ->
                    when {
                        a.startDato != b.startDato -> b.startDato.compareTo(a.startDato)
                        else -> b.dato.compareTo(a.dato)
                    }
                }.firstOrNull()

        return when (sisteHendelse) {
            is PersonSynkroniseringHendelse -> true
            is DagpengerMeldegruppeHendelse -> true
            is AnnenMeldegruppeHendelse -> false
            else -> false
        }
    }

    return false
}

fun harPersonsynkroniseringAvvik(person: Person): Boolean {
    if (person.status == Status.DAGPENGERBRUKER) {
        val meldegruppe =
            person.hendelser
                .filter { it is DagpengerMeldegruppeHendelse || it is AnnenMeldegruppeHendelse }
                .sortedWith { a, b ->
                    when {
                        a.startDato != b.startDato -> b.startDato.compareTo(a.startDato)
                        a.dato != b.dato -> b.dato.compareTo(a.dato)
                        a is DagpengerMeldegruppeHendelse && b is AnnenMeldegruppeHendelse -> -1
                        b is DagpengerMeldegruppeHendelse && a is AnnenMeldegruppeHendelse -> 1
                        else -> 0
                    }
                }.firstOrNull()

                ?.let {
                    when (it) {
                        is DagpengerMeldegruppeHendelse -> it.meldegruppeKode
                        is AnnenMeldegruppeHendelse -> it.meldegruppeKode
                        else -> null
                    }
                }

        return meldegruppe != null && meldegruppe != "DAGP"
    }

    return false
}

fun rettPersonSynkroniseringAvvik(person: Person) {
    person.hendelser
        .filterIsInstance<AnnenMeldegruppeHendelse>()
        .maxByOrNull { it.startDato }
        ?.let { annenMeldegruppeHendelse ->
            person.hendelser.removeIf { hendelse ->
                hendelse is PersonSynkroniseringHendelse &&
                    hendelse.startDato > annenMeldegruppeHendelse.startDato
            }
        }
}
