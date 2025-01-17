package no.nav.dagpenger.rapportering.personregister.mediator.db

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import no.nav.dagpenger.rapportering.personregister.mediator.db.Postgres.dataSource
import no.nav.dagpenger.rapportering.personregister.mediator.db.Postgres.withMigratedDb
import no.nav.dagpenger.rapportering.personregister.mediator.utils.MetrikkerTestUtil.actionTimer
import no.nav.dagpenger.rapportering.personregister.modell.Hendelse
import no.nav.dagpenger.rapportering.personregister.modell.Kildesystem
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.Status
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class PostgresPersonRepositoryTest {
    private var personRepository = PostgresPersonRepository(dataSource, actionTimer)

    @Test
    fun `skal lagre og finne person`() {
        withMigratedDb {
            val ident = "12345678901"
            val referanseId = "123"
            val dato = LocalDateTime.now()
            val person = Person(ident = ident)
            val hendelse =
                Hendelse(
                    ident = ident,
                    referanseId = referanseId,
                    dato = dato,
                    status = Status.SØKT,
                    kilde = Kildesystem.Søknad,
                )

            person.behandle(hendelse)
            personRepository.lagrePerson(person)

            personRepository.hentPerson(ident)?.apply {
                ident shouldBe ident
                hendelse shouldBe hendelse
                //                status shouldBe Status.SØKT
            }
        }
    }

    @Test
    fun `kan oppdatere person`() {
        withMigratedDb {
            val ident = "12345678901"
            val referanseId = "123"
            val dato = LocalDateTime.now().minusDays(1)
            val person = Person(ident = ident)
            val søknadHendelse =
                Hendelse(
                    ident = ident,
                    referanseId = referanseId,
                    dato = dato,
                    status = Status.SØKT,
                    kilde = Kildesystem.Søknad,
                )

            person.behandle(søknadHendelse)
            personRepository.lagrePerson(person)

            val vedtakHendelse =
                Hendelse(
                    ident = ident,
                    referanseId = "123",
                    dato = dato.plusDays(1),
                    status = Status.INNVILGET,
                    kilde = Kildesystem.Arena,
                )
            person.behandle(vedtakHendelse)
            personRepository.oppdaterPerson(person)

            personRepository.hentPerson(ident)?.apply {
                hendelser.size shouldBe 2
                status shouldBe Status.INNVILGET
            }
        }
    }

    @Test
    fun `oppdatering av person som ikke finnes i databasen kaster IllegalStateException`() {
        withMigratedDb {
            val person = Person(ident = "12345678901")
            shouldThrow<IllegalStateException> {
                personRepository.oppdaterPerson(person)
            }
        }
    }

    @Test
    fun `kan ikke hente person dersom person ikke finnes`() {
        withMigratedDb {
            personRepository.hentPerson("12345678901") shouldBe null
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
                Hendelse(
                    ident = ident,
                    referanseId = referanseId,
                    dato = dato,
                    status = Status.SØKT,
                    kilde = Kildesystem.Søknad,
                )

            person.behandle(hendelse)
            personRepository.lagrePerson(person)

            personRepository.hentAnallPersoner() shouldBe 1
            personRepository.hentAntallHendelser() shouldBe 1
        }
    }
}
