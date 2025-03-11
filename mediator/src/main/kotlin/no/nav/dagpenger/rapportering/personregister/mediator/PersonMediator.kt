package no.nav.dagpenger.rapportering.personregister.mediator

import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.modell.AnnenMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.DagpengerMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.Hendelse
import no.nav.dagpenger.rapportering.personregister.modell.MeldepliktHendelse
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.PersonObserver
import no.nav.dagpenger.rapportering.personregister.modell.PersonSynkroniseringHendelse
import no.nav.dagpenger.rapportering.personregister.modell.SøknadHendelse

class PersonMediator(
    private val personRepository: PersonRepository,
    private val arbeidssøkerMediator: ArbeidssøkerMediator,
    private val personObservers: List<PersonObserver> = emptyList(),
) {
    fun behandle(søknadHendelse: SøknadHendelse) {
        sikkerlogg.info { "Behandler søknadshendelse: $søknadHendelse" }
        hentEllerOpprettPerson(søknadHendelse.ident)
            .also {
                it.behandle(søknadHendelse)
                personRepository.oppdaterPerson(it)
                arbeidssøkerMediator.behandle(søknadHendelse.ident)
            }
    }

    fun behandle(hendelse: DagpengerMeldegruppeHendelse) {
        sikkerlogg.info { "Behandler dagpenger meldegruppe hendelse: $hendelse" }
        behandleHendelse(hendelse)
    }

    fun behandle(hendelse: AnnenMeldegruppeHendelse) {
        sikkerlogg.info { "Behandler annen meldegruppe hendelse: $hendelse" }
        behandleHendelse(hendelse)
    }

    fun behandle(hendelse: MeldepliktHendelse) {
        sikkerlogg.info { "Behandler meldeplikthendelse: $hendelse" }
        behandleHendelse(hendelse)
    }

    fun behandle(hendelse: PersonSynkroniseringHendelse) {
        sikkerlogg.info { "Behandler PersonSynkroniseringHendelse: $hendelse" }
        hentEllerOpprettPerson(hendelse.ident)
            .also { person ->
                person.behandle(hendelse)
                personRepository.oppdaterPerson(person)
                arbeidssøkerMediator.behandle(person.ident)
            }
    }

    private fun behandleHendelse(hendelse: Hendelse) {
        try {
            personRepository
                .hentPerson(hendelse.ident)
                ?.let { person ->
                    if (person.observers.isEmpty()) {
                        personObservers.forEach { person.addObserver(it) }
                    }
                    person.behandle(hendelse)
                    personRepository.oppdaterPerson(person)
                    sikkerlogg.info { "Hendelse behandlet: $hendelse" }
                }
        } catch (e: Exception) {
            sikkerlogg.info { "Feil ved behandling av hendelse: $hendelse" }
        }
    }

    private fun hentEllerOpprettPerson(ident: String): Person =
        personRepository
            .hentPerson(ident) ?: Person(ident)
            .also { person ->
                if (person.observers.isEmpty()) {
                    personObservers.forEach { observer -> person.addObserver(observer) }
                }
                personRepository.lagrePerson(person)
            }

    companion object {
        val sikkerlogg = KotlinLogging.logger("tjenestekall")
    }
}
