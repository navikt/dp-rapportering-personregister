package no.nav.dagpenger.rapportering.personregister.modell.features.steps

import io.cucumber.java8.No
import io.kotest.matchers.shouldBe
import no.nav.dagpenger.rapportering.personregister.modell.AvslagHendelse
import no.nav.dagpenger.rapportering.personregister.modell.InnvilgelseHendelse
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.StansHendelse
import no.nav.dagpenger.rapportering.personregister.modell.StartetArbeidssøkerperiodeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.Status
import no.nav.dagpenger.rapportering.personregister.modell.SøknadHendelse
import java.time.LocalDateTime
import java.util.UUID

class Steps : No {
    private lateinit var person: Person
    private val id = UUID.randomUUID().toString()
    private val ident = "12345678901"

    init {
        Gitt("en person") {
            person = Person(ident)
        }

        Gitt("en person som søkt om dagpenger den {string} med søknadId {string}") { søknadsdato: String, søknadId: String ->
            person = Person(ident)
            person.behandle(SøknadHendelse(ident, LocalDateTime.parse(søknadsdato), søknadId))
        }

        Gitt("en person som fikk avslag den {string} med avslagId {string}") { avslagsdato: String, avslagId: String ->
            person = Person(ident)
            person.behandle(AvslagHendelse(ident, LocalDateTime.parse(avslagsdato), avslagId))
        }

        Gitt("en person som fikk stans fra {string}") { stansDato: String ->
            person = Person(ident)
            person.behandle(StansHendelse(ident, LocalDateTime.parse(stansDato), "ARBS", "123"))
        }

        Gitt("en person som har dagpengerrettighet fra {string}") { dato: String ->
            person = Person(ident)
            person.behandle(InnvilgelseHendelse(ident, LocalDateTime.parse(dato), "123"))
        }

        Når("personen søker om dagpenger den {string} med søknadId {string}") { søknadsdato: String, søknadId: String ->
            person.behandle(SøknadHendelse(ident, LocalDateTime.parse(søknadsdato), søknadId))
        }

        Når("personen får vedtak om innvilgelse den {string} med vedtakId {string}") { vedtaksdato: String, vedtakId: String ->
            person.behandle(InnvilgelseHendelse(ident, LocalDateTime.parse(vedtaksdato), vedtakId))
        }

        Når("personen får vedtak om avslag den {string} med vedtakId {string}") { vedtaksdato: String, vedtakId: String ->
            person.behandle(AvslagHendelse(ident, LocalDateTime.parse(vedtaksdato), vedtakId))
        }

        Når("personen får vedtak om stans den {string} med vedtakId {string}") { vedtaksdato: String, vedtakId: String ->
            person.behandle(StansHendelse(ident, LocalDateTime.parse(vedtaksdato), "ARBS", vedtakId))
        }
        Når("personen klager og får medhold den {string} med vedtakId {string}") { vedtaksdato: String, vedtakId: String ->
            person.behandle(InnvilgelseHendelse(ident, LocalDateTime.parse(vedtaksdato), vedtakId))
        }

        Når("personen registrerer seg som arbeidssøker og søker dagpenger") {
            SøknadHendelse(
                ident,
                LocalDateTime.now(),
                UUID.randomUUID().toString(),
            ).apply { person.behandle(this) }

            StartetArbeidssøkerperiodeHendelse(
                UUID.randomUUID(),
                ident,
                LocalDateTime.now(),
            ).apply { håndter(person) }
        }

        Når("personen senere får innvilget dagpenger") {
            person.behandle(InnvilgelseHendelse(ident, LocalDateTime.now(), UUID.randomUUID().toString()))
        }

        Når("vedtaket til personen blir stanset") {
            person.behandle(StansHendelse(ident, LocalDateTime.now(), "ARBS", UUID.randomUUID().toString()))
        }

        Så("skal status være {string}") { status: String ->
            person.status.type shouldBe Status.Type.valueOf(status)
        }

        Og("vi skal ikke lenger ha ansvaret for personen") {
            person.arbeidssøkerperioder.first().overtattBekreftelse shouldBe true
        }

        Og("vi skal ha overtatt bekreftelse for personen") {
            person.arbeidssøkerperioder.first().overtattBekreftelse shouldBe true
        }

        Og("vi skal fortsatt ha ansvaret for personen") {
            person.arbeidssøkerperioder.first().overtattBekreftelse shouldBe true
        }

        Og("vi skal ikke lenger ha ansvaret for arbeidssøkeren") {
            person.arbeidssøkerperioder.first().overtattBekreftelse shouldBe false
        }
    }
}
