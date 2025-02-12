package no.nav.dagpenger.rapportering.personregister.mediator

import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.modell.AnnenMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.ArbeidssøkerHendelse
import no.nav.dagpenger.rapportering.personregister.modell.Hendelse
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.PersonObserver
import no.nav.dagpenger.rapportering.personregister.modell.SøknadHendelse

class PersonstatusMediator(
    private val personRepository: PersonRepository,
    private val arbeidssøkerMediator: ArbeidssøkerMediator,
    private val personObservers: List<PersonObserver> = emptyList(),
) {
    fun behandle(søknadHendelse: SøknadHendelse) {
        sikkerlogg.info { "Behandler søknadshendelse: $søknadHendelse" }
        behandleHendelse(søknadHendelse)
        arbeidssøkerMediator.behandle(søknadHendelse.ident)
    }

    fun behandle(hendelse: AnnenMeldegruppeHendelse) {
        sikkerlogg.info { "Behandler meldegruppeendringhendelse: $hendelse" }

        try {
            personRepository
                .hentPerson(hendelse.ident)
                ?.let { person ->
//                    if (person.status is INNVILGET &&
//                        hendelse.meldegruppeKode === "ARBS"
//                    ) {
//                        person.behandle(hendelse)
//                        personRepository.oppdaterPerson(person)
//                    }
                }

            sikkerlogg.info { "Behandlet hendelse: $hendelse" }
        } catch (e: Exception) {
            sikkerlogg.error(e) { "Feil ved behandling av hendelse: $hendelse" }
        }
    }

    fun behandle(arbeidssøkerHendelse: ArbeidssøkerHendelse) {
        sikkerlogg.info { "Behandler arbeidssøkerhendelse: $arbeidssøkerHendelse" }
        if (personRepository.finnesPerson(arbeidssøkerHendelse.ident)) {
            behandleHendelse(arbeidssøkerHendelse)
        } else {
            sikkerlogg.info { "Personen hendelsen gjelder for finnes ikke i databasen." }
        }
    }

    private fun behandleHendelse(hendelse: Hendelse) {
        try {
            personRepository
                .hentPerson(hendelse.ident)
                ?.let { person ->
                    personObservers.forEach { person.addObserver(it) }
                    person.behandle(hendelse)
                    personRepository.oppdaterPerson(person)
                } ?: run {
                Person(hendelse.ident).apply {
                    behandle(hendelse)
                    personRepository.lagrePerson(this)
                }
            }

            sikkerlogg.info { "Behandlet hendelse: $hendelse" }
        } catch (e: Exception) {
            sikkerlogg.error(e) { "Feil ved behandling av hendelse: $hendelse" }
        }
    }

    companion object {
        val sikkerlogg = KotlinLogging.logger("tjenestekall")
    }
}
