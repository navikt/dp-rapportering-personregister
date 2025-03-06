package no.nav.dagpenger.rapportering.personregister.modell.features.steps

import io.cucumber.java8.No
import io.kotest.matchers.shouldBe
import no.nav.dagpenger.rapportering.personregister.modell.AnnenMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.Arbeidssøkerperiode
import no.nav.dagpenger.rapportering.personregister.modell.DagpengerMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.Status
import no.nav.dagpenger.rapportering.personregister.modell.SøknadHendelse
import no.nav.dagpenger.rapportering.personregister.modell.gjeldende
import java.time.LocalDateTime
import java.util.UUID

class Steps : No {
    private lateinit var person: Person
    private val ident = "12345678901"
    private val nå = LocalDateTime.now()

    init {
        Gitt("en arbeidssøker") {
            person =
                Person(
                    ident,
                    arbeidssøkerperioder =
                        mutableListOf(
                            Arbeidssøkerperiode(
                                UUID.randomUUID(),
                                ident,
                                LocalDateTime.now(),
                                null,
                                false,
                            ),
                        ),
                )
        }

        Når("personen søker dagpenger") {
            SøknadHendelse(
                ident,
                LocalDateTime.now(),
                UUID.randomUUID().toString(),
            ).apply { person.behandle(this) }
        }

        Når("personen får meldeplikt og DAGP-meldegruppe") {
            person
                .apply { meldeplikt = true }
                .apply { behandle(dagpengerMeldegruppeHendelse()) }
        }

        Når("personen får innvilget dagpenger") {
            person.behandle(dagpengerMeldegruppeHendelse())
        }

        Når("vedtaket til personen blir stanset") {
            person.behandle(annenMeldegruppeHendelse())
        }

        Når("personen klager på vedtaket") {
            person.behandle(dagpengerMeldegruppeHendelse())
        }

        Når("personen får avslag") {
            person.behandle(annenMeldegruppeHendelse())
        }

        Når("personen får medhold i klagen") {
            person.behandle(dagpengerMeldegruppeHendelse())
        }

        Når("personen får avslag på klagen") {
            person.behandle(annenMeldegruppeHendelse())
        }

        Så("skal personen være {string}") { status: String ->
            person.status.type shouldBe Status.Type.valueOf(status)
        }

        Og("vi har overtatt bekreftelsen for personen") {
            person.arbeidssøkerperioder.gjeldende?.overtattBekreftelse shouldBe true
        }

        Og("vi beholder ansvaret for arbeidssøkerbekreftelse") {
            person.arbeidssøkerperioder.gjeldende?.overtattBekreftelse shouldBe true
        }

        Og("vi frasier oss ansvaret for personen") {
            person.arbeidssøkerperioder.gjeldende?.overtattBekreftelse shouldBe false
        }
    }

    private fun dagpengerMeldegruppeHendelse(
        dato: LocalDateTime = nå,
        referanseId: String = "123",
        meldegruppeKode: String = "DAGP",
    ) = DagpengerMeldegruppeHendelse(ident, dato, startDato = dato, sluttDato = null, meldegruppeKode, referanseId)

    private fun annenMeldegruppeHendelse(
        dato: LocalDateTime = nå,
        referanseId: String = "123",
    ) = AnnenMeldegruppeHendelse(ident, dato, startDato = dato, sluttDato = null, "ARBS", referanseId)
}
