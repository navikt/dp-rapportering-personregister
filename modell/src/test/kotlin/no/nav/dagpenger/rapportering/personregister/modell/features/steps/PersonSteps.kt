package no.nav.dagpenger.rapportering.personregister.modell.features.steps

import io.cucumber.java8.No
import io.kotest.matchers.shouldBe
import no.nav.dagpenger.rapportering.personregister.modell.Hendelse
import no.nav.dagpenger.rapportering.personregister.modell.Kildesystem
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.Status
import java.time.LocalDateTime

class PersonSteps : No {
    private lateinit var person: Person
    private val ident = "12345678901"

    init {
        Gitt("en person") {
            person = Person(ident)
        }

        Gitt("en person som søkt om dagpenger den {string} med søknadId {string}") { søknadsdato: String, søknadId: String ->
            person = Person(ident)
            person.behandle(lagHendelse(søknadsdato, søknadId, Status.Type.SØKT, Kildesystem.Søknad))
            println(person)
        }

        Gitt("en person som fikk avslag den {string} med avslagId {string}") { avslagsdato: String, avslagId: String ->
            person = Person(ident)
            person.behandle(lagHendelse(avslagsdato, avslagId, Status.Type.AVSLÅTT, Kildesystem.Arena))
        }

        Gitt("en person som fikk stans fra {string}") { stansDato: String ->
            person = Person(ident)
            person.behandle(lagHendelse(stansDato, "123", Status.Type.STANSET, Kildesystem.Arena))
        }

        Gitt("en person som har dagpengerrettighet fra {string}") { dato: String ->
            person = Person(ident)
            person.behandle(lagHendelse(dato, "123", Status.Type.INNVILGET, Kildesystem.Arena))
        }

        Når("personen søker om dagpenger den {string} med søknadId {string}") { søknadsdato: String, søknadId: String ->
            person.behandle(lagHendelse(søknadsdato, søknadId, Status.Type.SØKT, Kildesystem.Søknad))
        }

        Når("personen får vedtak om innvilgelse den {string} med vedtakId {string}") { vedtaksdato: String, vedtakId: String ->
            person.behandle(lagHendelse(vedtaksdato, vedtakId, Status.Type.INNVILGET, Kildesystem.Arena))
            println(person)
        }

        Når("personen får vedtak om avslag den {string} med vedtakId {string}") { vedtaksdato: String, vedtakId: String ->
            person.behandle(lagHendelse(vedtaksdato, vedtakId, Status.Type.AVSLÅTT, Kildesystem.Arena))
        }

        Når("personen får vedtak om stans den {string} med vedtakId {string}") { vedtaksdato: String, vedtakId: String ->
            person.behandle(lagHendelse(vedtaksdato, vedtakId, Status.Type.STANSET, Kildesystem.Arena))
        }
        Når("personen klager og får medhold den {string} med vedtakId {string}") { vedtaksdato: String, vedtakId: String ->
            person.behandle(lagHendelse(vedtaksdato, vedtakId, Status.Type.INNVILGET, Kildesystem.Arena))
        }

        Så("skal status være {string}") { status: String ->
            person.status.type shouldBe Status.Type.valueOf(status)
        }
    }

    private fun lagHendelse(
        dato: String,
        referanseId: String,
        status: Status.Type,
        kilde: Kildesystem,
    ) = Hendelse(ident = ident, referanseId = referanseId, dato = LocalDateTime.parse(dato), status = status, kilde = kilde).apply {
    }
}
