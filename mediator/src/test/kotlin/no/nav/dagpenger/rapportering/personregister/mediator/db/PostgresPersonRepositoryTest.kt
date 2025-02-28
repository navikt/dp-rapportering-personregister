package no.nav.dagpenger.rapportering.personregister.mediator.db

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import no.nav.dagpenger.rapportering.personregister.mediator.db.Postgres.dataSource
import no.nav.dagpenger.rapportering.personregister.mediator.db.Postgres.withMigratedDb
import no.nav.dagpenger.rapportering.personregister.mediator.utils.MetrikkerTestUtil.actionTimer
import no.nav.dagpenger.rapportering.personregister.modell.DagpengerMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.Dagpengerbruker
import no.nav.dagpenger.rapportering.personregister.modell.IkkeDagpengerbruker
import no.nav.dagpenger.rapportering.personregister.modell.MeldepliktHendelse
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.SøknadHendelse
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class PostgresPersonRepositoryTest {
    private var personRepository = PostgresPersonRepository(dataSource, actionTimer)

    private val ident = "12345678901"

    @Test
    @Disabled
    fun `skal lagre og finne person`() {
        withMigratedDb {
            val referanseId = "123"
            val dato = LocalDateTime.now()
            val person = Person(ident = ident)
            val hendelse =
                SøknadHendelse(
                    ident = ident,
                    referanseId = referanseId,
                    dato = dato,
                )

            person.behandle(hendelse)
            personRepository.lagrePerson(person)

            personRepository.hentPerson(ident)?.apply {
                ident shouldBe ident
                hendelse shouldBe hendelse
                status shouldBe Dagpengerbruker
            }
        }
    }

    @Test
    @Disabled
    fun `kan oppdatere person`() {
        withMigratedDb {
            val referanseId = "123"
            val dato = LocalDateTime.now().minusDays(1)
            val person = Person(ident = ident)
            val søknadHendelse =
                SøknadHendelse(
                    ident = ident,
                    dato = dato,
                    referanseId = referanseId,
                )

            person.behandle(søknadHendelse)
            personRepository.lagrePerson(person)

            val hendelse =
                DagpengerMeldegruppeHendelse(
                    ident = ident,
                    referanseId = "456",
                    dato = dato.plusDays(1),
                    startDato = dato,
                    sluttDato = null,
                    meldegruppeKode = "DAGP",
                )
            person.behandle(hendelse)
            personRepository.oppdaterPerson(person)

            personRepository.hentPerson(ident)?.apply {
//                hendelser.size shouldBe 2 // TODO Fix this
                status shouldBe IkkeDagpengerbruker
            }
        }
    }

    @Test
    fun `oppdatering av person som ikke finnes i databasen kaster IllegalStateException`() {
        withMigratedDb {
            val person = Person(ident = ident)
            shouldThrow<IllegalStateException> {
                personRepository.oppdaterPerson(person)
            }
        }
    }

    @Test
    fun `kan ikke hente person dersom person ikke finnes`() {
        withMigratedDb {
            personRepository.hentPerson(ident) shouldBe null
        }
    }

    @Test
    fun `kan sjekke om person finnes i databasen hvis personen finnes`() {
        val referanseId = "123"
        val dato = LocalDateTime.now()
        withMigratedDb {
            val person = Person(ident = ident)
            val hendelse =
                SøknadHendelse(
                    ident = ident,
                    referanseId = referanseId,
                    dato = dato,
                )

            person.behandle(hendelse)
            personRepository.lagrePerson(person)

            personRepository.finnesPerson(ident) shouldBe true
        }
    }

    @Test
    fun `kan sjekke om person finnes i databasen hvis personen ikke finnes`() {
        withMigratedDb {
            personRepository.finnesPerson(ident) shouldBe false
        }
    }

    @Test
    fun `kan hente antall personsoner og hendelser i databasen`() {
        withMigratedDb {
            val ident = "12345678901"
            val referanseId = "123"
            val dato = LocalDateTime.now()
            val person = Person(ident = ident)
            val hendelse =
                SøknadHendelse(
                    ident = ident,
                    referanseId = referanseId,
                    dato = dato,
                )

            person.behandle(hendelse)
            personRepository.lagrePerson(person)

            personRepository.hentAnallPersoner() shouldBe 1
            personRepository.hentAntallHendelser() shouldBe 1
        }
    }

    @Test
    fun `kan lagre, hente og slette elementer i fremtidig_hendelse`() {
        withMigratedDb {
            val nå = LocalDateTime.now()
            val person = Person(ident = ident)
            val meldepliktHendelse =
                MeldepliktHendelse(
                    ident = ident,
                    referanseId = "123",
                    dato = nå.minusDays(2),
                    startDato = nå,
                    sluttDato = null,
                    statusMeldeplikt = true,
                )
            val meldegruppeHendelse =
                DagpengerMeldegruppeHendelse(
                    ident = ident,
                    referanseId = "321",
                    dato = nå.minusDays(1),
                    startDato = nå,
                    sluttDato = null,
                    meldegruppeKode = "DAGP",
                )

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
    }

    @Test
    fun `hendelser med samme referanse overskriver hverandre`() {
        withMigratedDb {
            val referanseId = "123"
            val nå = LocalDateTime.now()
            val person = Person(ident = ident)
            val meldepliktHendelse =
                MeldepliktHendelse(
                    ident = ident,
                    referanseId = referanseId,
                    dato = nå.minusDays(2),
                    startDato = nå,
                    sluttDato = null,
                    statusMeldeplikt = true,
                )
            val meldegruppeHendelse =
                DagpengerMeldegruppeHendelse(
                    ident = ident,
                    referanseId = referanseId,
                    dato = nå.minusDays(1),
                    startDato = nå,
                    sluttDato = null,
                    meldegruppeKode = "DAGP",
                )

            personRepository.lagrePerson(person)

            personRepository.lagreFremtidigHendelse(meldegruppeHendelse)
            with(personRepository.hentHendelserSomSkalAktiveres()) {
                size shouldBe 1
                with(first()) {
                    referanseId shouldBe referanseId
                    javaClass shouldBe DagpengerMeldegruppeHendelse::class.java
                }
            }

            personRepository.lagreFremtidigHendelse(meldepliktHendelse)
            with(personRepository.hentHendelserSomSkalAktiveres()) {
                size shouldBe 1
                with(first()) {
                    referanseId shouldBe referanseId
                    javaClass shouldBe MeldepliktHendelse::class.java
                }
            }
        }
    }
}
