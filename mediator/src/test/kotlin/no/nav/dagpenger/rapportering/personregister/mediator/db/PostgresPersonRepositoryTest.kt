package no.nav.dagpenger.rapportering.personregister.mediator.db

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import no.nav.dagpenger.rapportering.personregister.mediator.db.Postgres.dataSource
import no.nav.dagpenger.rapportering.personregister.mediator.db.Postgres.withMigratedDb
import no.nav.dagpenger.rapportering.personregister.mediator.utils.MetrikkerTestUtil.actionTimer
import no.nav.dagpenger.rapportering.personregister.modell.DagpengerMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.Dagpengerbruker
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.SøknadHendelse
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class PostgresPersonRepositoryTest {
    private var personRepository = PostgresPersonRepository(dataSource, actionTimer)

    private val ident = "12345678901"

    @Test
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
                    meldegruppeKode = "DAGP",
                )
            person.behandle(hendelse)
            personRepository.oppdaterPerson(person)

            personRepository.hentPerson(ident)?.apply {
                hendelser.size shouldBe 2
                status shouldBe Dagpengerbruker
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
}
