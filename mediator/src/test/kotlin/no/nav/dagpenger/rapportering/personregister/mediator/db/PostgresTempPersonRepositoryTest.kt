package no.nav.dagpenger.rapportering.personregister.mediator.db

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import no.nav.dagpenger.rapportering.personregister.mediator.db.Postgres.dataSource
import no.nav.dagpenger.rapportering.personregister.mediator.db.Postgres.withMigratedDb
import no.nav.dagpenger.rapportering.personregister.mediator.utils.MetrikkerTestUtil.actionTimer
import no.nav.dagpenger.rapportering.personregister.modell.Person
import org.junit.jupiter.api.Test

class PostgresTempPersonRepositoryTest {
    private val repository =
        PostgresTempPersonRepository(
            dataSource = dataSource,
        )
    private val personRepository =
        PostgresPersonRepository(
            dataSource = dataSource,
            actionTimer = actionTimer,
        )

    @Test
    fun `returnerer null når person ikke eksisterer`() =

        withMigratedDb {
            val ident = "12345678901"

            repository.hentPerson(ident) shouldBe null
        }

    @Test
    fun `lagrer og henter person`() =
        withMigratedDb {
            val ident = "12345678901"

            repository.lagrePerson(TempPerson(ident))

            repository.hentPerson(ident)?.apply {
                ident shouldBe ident
                status shouldBe TempPersonStatus.IKKE_PABEGYNT
            }
        }

    @Test
    fun `kan ikke lagre eksisterende person`() =
        withMigratedDb {
            val ident = "12345678901"
            val person = TempPerson(ident)
            repository.lagrePerson(person)

            repository.hentPerson(ident)?.apply {
                ident shouldBe ident
                status shouldBe TempPersonStatus.IKKE_PABEGYNT
            }

            person.status = TempPersonStatus.FERDIGSTILT
            repository.oppdaterPerson(person)

            repository.hentPerson(ident)?.apply {
                ident shouldBe ident
                status shouldBe TempPersonStatus.FERDIGSTILT
            }
        }

    @Test
    fun `kan oppdatere person`() =
        withMigratedDb {
            val ident = "12345678901"
            val person = TempPerson(ident)

            shouldThrow<IllegalArgumentException> {
                repository.lagrePerson(person)
                repository.lagrePerson(person)
            }
        }

    @Test
    fun `sletter person`() =
        withMigratedDb {
            val ident = "12345678901"
            val person = TempPerson(ident)

            repository.lagrePerson(person)

            repository.hentPerson(ident)?.ident shouldBe ident

            repository.slettPerson(ident)

            repository.hentPerson(ident) shouldBe null
        }

    @Test
    fun `henter alle identer person`() =
        withMigratedDb {
            val person1 = TempPerson("12345678901")
            val person2 = TempPerson("12345678904")

            repository.lagrePerson(person1)
            repository.lagrePerson(person2)

            repository.hentAlleIdenter() shouldBe
                listOf(
                    person1.ident,
                    person2.ident,
                )
        }

    @Test
    fun `syncer personer fra person tabell`() =
        withMigratedDb {
            val person1 = Person("12345678901")
            val person2 = Person("12345678902")
            val person3 = Person("12345678903")

            personRepository.lagrePerson(person1)
            personRepository.lagrePerson(person2)
            personRepository.lagrePerson(person3)

            personRepository.hentAntallPersoner() shouldBe 3

            repository.syncPersoner()

            repository.isEmpty() shouldBe false
            repository.hentAlleIdenter() shouldBe
                listOf(
                    person1.ident,
                    person2.ident,
                    person3.ident,
                )
        }
}
