package no.nav.dagpenger.rapportering.personregister.mediator

import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.service.ArbeidssøkerService
import no.nav.dagpenger.rapportering.personregister.modell.AvslagHendelse
import no.nav.dagpenger.rapportering.personregister.modell.Hendelse
import no.nav.dagpenger.rapportering.personregister.modell.INNVILGET
import no.nav.dagpenger.rapportering.personregister.modell.InnvilgelseHendelse
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.StansHendelse
import no.nav.dagpenger.rapportering.personregister.modell.SøknadHendelse

class PersonstatusMediator(
    private val personRepository: PersonRepository,
    private val arbeidssøkerService: ArbeidssøkerService,
) {
    fun behandle(søknadHendelse: SøknadHendelse) {
        sikkerlogg.info { "Behandler søknadshendelse: $søknadHendelse" }
        arbeidssøkerService.sendArbeidssøkerBehov(søknadHendelse.ident)
        behandleHendelse(søknadHendelse)
    }

    fun behandle(hendelse: InnvilgelseHendelse) {
        sikkerlogg.info { "Behandler vedtakshendelse: $hendelse" }
        behandleHendelse(hendelse)
    }

    fun behandle(hendelse: AvslagHendelse) {
        sikkerlogg.info { "Behandler vedtakshendelse: $hendelse" }
        behandleHendelse(hendelse)
    }

    fun behandle(hendelse: StansHendelse) {
        sikkerlogg.info { "Behandler meldegruppeendringhendelse: $hendelse" }

        try {
            personRepository
                .hentPerson(hendelse.ident)
                ?.let { person ->
                    if (person.status is INNVILGET &&
                        hendelse.meldegruppeKode === "ARBS"
                    ) {
                        person.behandle(hendelse)
                        personRepository.oppdaterPerson(person)
                    }
                }

            sikkerlogg.info { "Behandlet hendelse: $hendelse" }
        } catch (e: Exception) {
            sikkerlogg.error(e) { "Feil ved behandling av hendelse: $hendelse" }
        }
    }

    fun behandle(arbeidssøkerHendelse: no.nav.dagpenger.rapportering.personregister.modell.ArbeidssøkerHendelse) {
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
