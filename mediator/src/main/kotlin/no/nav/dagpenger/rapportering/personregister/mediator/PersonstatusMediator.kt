package no.nav.dagpenger.rapportering.personregister.mediator

import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.hendelser.ArbeidssøkerHendelse
import no.nav.dagpenger.rapportering.personregister.mediator.hendelser.MeldegruppeendringHendelse
import no.nav.dagpenger.rapportering.personregister.mediator.hendelser.SøknadHendelse
import no.nav.dagpenger.rapportering.personregister.mediator.hendelser.VedtakHendelse
import no.nav.dagpenger.rapportering.personregister.mediator.hendelser.tilHendelse
import no.nav.dagpenger.rapportering.personregister.mediator.service.ArbeidssøkerService
import no.nav.dagpenger.rapportering.personregister.modell.Hendelse
import no.nav.dagpenger.rapportering.personregister.modell.Kildesystem
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.Status
import java.util.UUID

class PersonstatusMediator(
    private val personRepository: PersonRepository,
    private val arbeidssøkerService: ArbeidssøkerService,
) {
    fun behandle(søknadHendelse: SøknadHendelse) {
        sikkerlogg.info { "Behandler søknadshendelse: $søknadHendelse" }
        arbeidssøkerService.sendArbeidssøkerBehov(søknadHendelse.ident)
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

    fun behandle(meldegruppeendringHendelse: MeldegruppeendringHendelse) {
        sikkerlogg.info { "Behandler meldegruppeendringhendelse: $meldegruppeendringHendelse" }

        try {
            val ident = meldegruppeendringHendelse.ident
            val meldegruppeKode = meldegruppeendringHendelse.meldegruppeKode

            personRepository
                .hentPerson(ident)
                ?.let { person ->
                    if (person.status.type === Status.Type.INNVILGET && meldegruppeKode === "ARBS") {
                        val hendelse =
                            Hendelse(
                                id = UUID.randomUUID(),
                                ident = ident,
                                referanseId = meldegruppeendringHendelse.hendelseId,
                                dato = meldegruppeendringHendelse.fraOgMed.atStartOfDay(),
                                status = Status.Type.STANSET,
                                kilde = Kildesystem.Arena,
                            )
                        person.behandle(hendelse)
                        personRepository.oppdaterPerson(person)
                    }
                }

            sikkerlogg.info { "Behandlet hendelse: $meldegruppeendringHendelse" }
        } catch (e: Exception) {
            sikkerlogg.error(e) { "Feil ved behandling av hendelse: $meldegruppeendringHendelse" }
        }
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
