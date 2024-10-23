package no.nav.dagpenger.rapportering.personregister.mediator

import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.hendelser.SøknadHendelse
import no.nav.dagpenger.rapportering.personregister.mediator.hendelser.VedtakHendelse
import no.nav.dagpenger.rapportering.personregister.mediator.hendelser.tilHendelse
import no.nav.dagpenger.rapportering.personregister.modell.Hendelse
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.RegistrertSomArbeidssøkerLøsning
import java.time.LocalDate
import java.util.UUID

class PersonstatusMediator(
    private val personRepository: PersonRepository,
) {
    fun behandle(søknadHendelse: SøknadHendelse) {
        sikkerlogg.info { "Behandler søknadshendelse: $søknadHendelse" }
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

    fun behandle(løsning: RegistrertSomArbeidssøkerLøsning) {
        try {
            personRepository
                .hentPerson(løsning.ident)
                ?.let { person ->
                    if (løsning.gyldigFraOgMed.isBefore(LocalDate.now().plusDays(1)) && løsning.gyldigTilOgMed.isAfter(LocalDate.now())) {
                        person.settArbeidssøker(løsning.verdi)
                        personRepository.oppdaterPerson(person)
                        sikkerlogg.info { "Oppdatert person med arbeidssøkerstatus: $løsning" }
                    } else {
                        sikkerlogg.warn { "Fikk ugyldig virkningsdato for løsning: $løsning" }
                    }
                } ?: run {
                sikkerlogg.warn { "Fikk løsning for person som ikke finnes: $løsning" }
            }
        } catch (e: Exception) {
            sikkerlogg.error(e) { "Feil ved behandling av løsning: $løsning" }
        }
    }

    companion object {
        val sikkerlogg = KotlinLogging.logger("tjenestekall")
    }
}
