package no.nav.dagpenger.rapportering.personregister.mediator.db

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import no.nav.dagpenger.rapportering.personregister.mediator.db.Postgres.dataSource
import no.nav.dagpenger.rapportering.personregister.mediator.db.Postgres.withMigratedDb
import no.nav.dagpenger.rapportering.personregister.mediator.utils.MetrikkerTestUtil.actionTimer
import no.nav.dagpenger.rapportering.personregister.modell.Arbeidssøkerperiode
import no.nav.dagpenger.rapportering.personregister.modell.DagpengerMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.Hendelse
import no.nav.dagpenger.rapportering.personregister.modell.MeldepliktHendelse
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.Status
import no.nav.dagpenger.rapportering.personregister.modell.Status.DAGPENGERBRUKER
import no.nav.dagpenger.rapportering.personregister.modell.SøknadHendelse
import no.nav.dagpenger.rapportering.personregister.modell.TemporalCollection
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class PostgresPersonRepositoryTest {
    private val personRepository = PostgresPersonRepository(dataSource, actionTimer)
    private val ident = "12345678901"
    private val nå = LocalDateTime.now()

    @Test
    @Disabled
    fun `kan lagre og hente komplett person`() =
        withMigratedDb {
            val person =
                testPerson(
                    hendelser = mutableListOf(søknadHendelse()),
                    arbeidssøkerperiode = mutableListOf(arbeidssøkerperiode()),
                )
            personRepository.lagrePerson(person)

            personRepository.hentPerson(ident)?.apply {
                ident shouldBe person.ident
                hendelser shouldBe person.hendelser
                status shouldBe person.status
                arbeidssøkerperioder shouldBe person.arbeidssøkerperioder
            }
        }

    @Test
    @Disabled
    fun `kan oppdatere person`() =
        withMigratedDb {
            val person =
                testPerson(
                    hendelser = mutableListOf(søknadHendelse()),
                    arbeidssøkerperiode = mutableListOf(arbeidssøkerperiode()),
                )
            personRepository.lagrePerson(person)

            val nyHendelse = dagpengerMeldegruppeHendelse()
            person.hendelser.add(nyHendelse)
            person.meldeplikt = true
            personRepository.oppdaterPerson(person)

            personRepository.hentPerson(ident)?.apply {
                hendelser shouldBe listOf(person.hendelser.first(), nyHendelse)
            }
        }

    @Test
    fun `oppdatering av ikke-eksisterende person kaster IllegalStateException`() =
        withMigratedDb {
            shouldThrow<IllegalStateException> { personRepository.oppdaterPerson(Person(ident)) }
        }

    @Test
    fun `kan ikke hente ikke-eksisterende person`() =
        withMigratedDb {
            personRepository.hentPerson(ident) shouldBe null
        }

    @Test
    fun `kan sjekke om person finnes`() =
        withMigratedDb {
            val person = Person(ident)
            personRepository.finnesPerson(ident) shouldBe false
            personRepository.lagrePerson(person)
            personRepository.finnesPerson(ident) shouldBe true
        }

    @Test
    fun `kan hente antall personer og hendelser`() =
        withMigratedDb {
            val person = testPerson(hendelser = mutableListOf(søknadHendelse()))
            personRepository.lagrePerson(person)
            personRepository.hentAnallPersoner() shouldBe 1
            personRepository.hentAntallHendelser() shouldBe person.hendelser.size
        }

    @Test
    fun `kan lagre, hente og slette fremtidige hendeslser`() =
        withMigratedDb {
            val person = Person(ident = ident)
            val meldepliktHendelse = meldepliktHendelse()
            val meldegruppeHendelse = dagpengerMeldegruppeHendelse()

            personRepository.lagrePerson(person)
            personRepository.lagreFremtidigHendelse(meldegruppeHendelse)
            personRepository.lagreFremtidigHendelse(meldepliktHendelse)

            with(personRepository.hentHendelserSomSkalAktiveres()) {
                size shouldBe 2
                first().javaClass shouldBe MeldepliktHendelse::class.java
                last().javaClass shouldBe DagpengerMeldegruppeHendelse::class.java
            }

            personRepository.slettFremtidigHendelse(meldepliktHendelse.referanseId)
            personRepository.slettFremtidigHendelse(meldegruppeHendelse.referanseId)

            personRepository.hentHendelserSomSkalAktiveres().size shouldBe 0
        }

    @Test
    fun `hendelser med samme referanse overskriver hverandre`() =
        withMigratedDb {
            val person = Person(ident)
            val referanseId = UUID.randomUUID().toString()
            val førsteHendelse = dagpengerMeldegruppeHendelse(referanseId)
            val nyHendelse = meldepliktHendelse(referanseId)

            personRepository.lagrePerson(person)
            personRepository.lagreFremtidigHendelse(førsteHendelse)
            personRepository.hentHendelserSomSkalAktiveres().first().javaClass shouldBe førsteHendelse.javaClass

            personRepository.lagreFremtidigHendelse(nyHendelse)
            personRepository.hentHendelserSomSkalAktiveres().first().javaClass shouldBe nyHendelse.javaClass
        }

    private fun testPerson(
        hendelser: MutableList<Hendelse> = mutableListOf(),
        arbeidssøkerperiode: MutableList<Arbeidssøkerperiode> = mutableListOf(),
    ) = Person(
        ident = ident,
        statusHistorikk = statusHistorikk(mapOf(nå to DAGPENGERBRUKER)),
        arbeidssøkerperioder = arbeidssøkerperiode,
    ).apply { this.hendelser.addAll(hendelser) }

    private fun søknadHendelse() =
        SøknadHendelse(
            ident = "12345678901",
            referanseId = UUID.randomUUID().toString(),
            dato = LocalDateTime.now(),
        )

    private fun arbeidssøkerperiode() =
        Arbeidssøkerperiode(
            ident = "12345678901",
            periodeId = UUID.randomUUID(),
            startet = LocalDateTime.now(),
            avsluttet = null,
            overtattBekreftelse = false,
        )

    private fun statusHistorikk(historikk: Map<LocalDateTime, Status>) =
        TemporalCollection<Status>().apply {
            historikk.forEach { (dato, status) -> put(dato, status) }
        }

    private fun dagpengerMeldegruppeHendelse(referanseId: String = UUID.randomUUID().toString()) =
        DagpengerMeldegruppeHendelse(
            ident = "12345678901",
            referanseId = referanseId,
            dato = LocalDateTime.now(),
            startDato = LocalDateTime.now(),
            sluttDato = null,
            meldegruppeKode = "DAGP",
        )

    private fun meldepliktHendelse(referanseId: String = UUID.randomUUID().toString()) =
        MeldepliktHendelse(
            ident = "12345678901",
            referanseId = referanseId,
            dato = LocalDateTime.now(),
            startDato = LocalDateTime.now(),
            sluttDato = null,
            statusMeldeplikt = true,
        )
}
