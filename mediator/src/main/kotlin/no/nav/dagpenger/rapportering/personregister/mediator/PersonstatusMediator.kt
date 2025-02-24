package no.nav.dagpenger.rapportering.personregister.mediator

import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.modell.AnnenMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.DagpengerMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.Hendelse
import no.nav.dagpenger.rapportering.personregister.modell.MeldepliktHendelse
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
        behandle(søknadHendelse) { arbeidssøkerMediator.behandle(it.ident) }
    }

    fun behandle(hendelse: DagpengerMeldegruppeHendelse) {
        sikkerlogg.info { "Behandler dagpenger meldegruppe hendelse: $hendelse" }
        behandle(hendelse) {}
    }

    fun behandle(hendelse: AnnenMeldegruppeHendelse) {
        sikkerlogg.info { "Behandler annen meldegruppe hendelse: $hendelse" }
        behandle(hendelse) {}
    }

    fun behandle(hendelse: MeldepliktHendelse) {
        sikkerlogg.info { "Behandler meldeplikthendelse: $hendelse" }
        behandle(hendelse) {}
    }

    private fun behandle(
        hendelse: Hendelse,
        håndter: (Hendelse) -> Unit = {},
    ) {
        try {
            val person = hentEllerOpprettPerson(hendelse.ident)
            personObservers.forEach { person.addObserver(it) }
            person.behandle(hendelse)
            personRepository.oppdaterPerson(person)
            sikkerlogg.info { "Hendelse behandlet: $hendelse" }
            håndter(hendelse)
        } catch (e: Exception) {
            sikkerlogg.info { "Feil ved behandling av hendelse: $hendelse" }
        }
    }

    private fun hentEllerOpprettPerson(ident: String): Person =
        personRepository
            .hentPerson(ident) ?: Person(ident)
            .also {
                personRepository.lagrePerson(it)
            }

    companion object {
        val sikkerlogg = KotlinLogging.logger("tjenestekall")
    }
}
