package no.nav.dagpenger.rapportering.personregister.mediator

import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.hendelser.ArbeidssøkerHendelse
import no.nav.dagpenger.rapportering.personregister.mediator.hendelser.SøknadHendelse
import no.nav.dagpenger.rapportering.personregister.mediator.hendelser.VedtakHendelse
import no.nav.dagpenger.rapportering.personregister.mediator.hendelser.tilHendelse
import no.nav.dagpenger.rapportering.personregister.mediator.service.ArbeidssøkerService
import no.nav.dagpenger.rapportering.personregister.modell.Hendelse
import no.nav.dagpenger.rapportering.personregister.modell.Person
import java.util.UUID

class PersonstatusMediator(
    private val personRepository: PersonRepository,
    private val arbeidssøkerService: ArbeidssøkerService,
) {
    fun behandle(søknadHendelse: SøknadHendelse) {
        sikkerlogg.info { "Behandler søknadshendelse: $søknadHendelse" }
        runBlocking { arbeidssøkerService.hentArbeidssøkerHendelse(søknadHendelse.ident) }
            ?.let { arbeidssøkerHendelse ->
                behandleHendelse(arbeidssøkerHendelse.tilHendelse())
            }
        behandleHendelse(søknadHendelse.tilHendelse())
    }

    fun behandle(vedtakHendelse: VedtakHendelse) {
        val hendelse =
            Hendelse(
                id = UUID.randomUUID(),
                ident = vedtakHendelse.ident,
                referanseId = vedtakHendelse.referanseId,
                dato = vedtakHendelse.dato,
                status = vedtakHendelse.status,
                kilde = vedtakHendelse.kildesystem,
            )

        sikkerlogg.info { "Behandler vedtakshendelse: $hendelse" }
        behandleHendelse(hendelse)
    }

    fun behandle(arbeidssøkerHendelse: ArbeidssøkerHendelse) {
        sikkerlogg.info { "Behandler arbeidssøkerhendelse: $arbeidssøkerHendelse" }
        if (personRepository.finnesPerson(arbeidssøkerHendelse.ident)) {
            behandleHendelse(arbeidssøkerHendelse.tilHendelse())
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
