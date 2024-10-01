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

        Gitt("en person søkt om dagpenger den {string} med søknadId {string}") { søknadsdato: String, søknadId: String ->
            person = Person(ident)
            person.behandle(lagHendelse(søknadsdato, søknadId, Status.SØKT, Kildesystem.Søknad))
        }

        Gitt("en person som fikk avslag") {
            person = Person(ident)
            person.behandle(lagHendelse(LocalDateTime.now().toString(), "123", Status.AVSLÅTT, Kildesystem.Arena))
        }

        Gitt("en person har dagpengerrettighet") {
            person = Person(ident)
            person.behandle(lagHendelse(LocalDateTime.now().toString(), "123", Status.INNVILGET, Kildesystem.Arena))
        }

        Når("personen søker om dagpenger den {string} med søknadId {string}") { søknadsdato: String, søknadId: String ->
            person.behandle(lagHendelse(søknadsdato, søknadId, Status.SØKT, Kildesystem.Søknad))
        }

        Når("personen får vedtak om innvilgelse den {string} med vedtakId {string}") { vedtaksdato: String, vedtakId: String ->
            person.behandle(lagHendelse(vedtaksdato, vedtakId, Status.INNVILGET, Kildesystem.Arena))
        }

        Når("personen får vedtak om avslag den {string} med vedtakId {string}") { vedtaksdato: String, vedtakId: String ->
            person.behandle(lagHendelse(vedtaksdato, vedtakId, Status.AVSLÅTT, Kildesystem.Arena))
        }
        Når("personen klager og får medhold den {string} med vedtakId {string}") { vedtaksdato: String, vedtakId: String ->
            person.behandle(lagHendelse(vedtaksdato, vedtakId, Status.INNVILGET, Kildesystem.Arena))
        }

        Så("skal status være {string}") { status: String ->
            person.status shouldBe Status.valueOf(status)
        }
    }

    private fun lagHendelse(
        dato: String,
        referanseId: String,
        status: Status,
        kilde: Kildesystem,
    ) = Hendelse(ident = ident, referanseId = referanseId, dato = LocalDateTime.parse(dato), status = status, kilde = kilde).apply {
    }
}
