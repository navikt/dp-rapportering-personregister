package no.nav.dagpenger.rapportering.personregister.mediator.db

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.dagpenger.rapportering.personregister.mediator.db.Postgres.dataSource
import no.nav.dagpenger.rapportering.personregister.mediator.db.Postgres.withMigratedDb
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.ArbeidssøkerBeslutning
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.Handling
import no.nav.dagpenger.rapportering.personregister.mediator.utils.MetrikkerTestUtil.actionTimer
import no.nav.dagpenger.rapportering.personregister.modell.Person
import org.junit.jupiter.api.Test
import java.util.UUID

class PostgressArbeidssøkerBeslutningRepositoryTest {
    private val personRepository = PostgresPersonRepository(dataSource, actionTimer)
    private val arbeidssøkerBeslutningRepository = PostgressArbeidssøkerBeslutningRepository(dataSource, actionTimer)

    @Test
    fun `kan lagre og hente arbeidssøkerbeslutning`() {
        withMigratedDb {
            val person = Person("12345678901").also { personRepository.lagrePerson(it) }

            val beslutning =
                ArbeidssøkerBeslutning(
                    ident = person.ident,
                    periodeId = UUID.randomUUID(),
                    handling = Handling.OVERTATT,
                    begrunnelse = "Test begrunnelse",
                )

            arbeidssøkerBeslutningRepository.lagreBeslutning(beslutning)
            arbeidssøkerBeslutningRepository.hentBeslutning(person.ident) shouldNotBe null
        }
    }

    @Test
    fun `kan hente liste av arbeidsøkerbeslutninger`() {
        withMigratedDb {
            val person = Person("12345678901").also { personRepository.lagrePerson(it) }

            val overtattArbeidssøkerBeslutning =
                ArbeidssøkerBeslutning(
                    ident = person.ident,
                    periodeId = UUID.randomUUID(),
                    handling = Handling.OVERTATT,
                    begrunnelse = "Test begrunnelse",
                )
            val frasagtArbeidssøkerBeslutning =
                ArbeidssøkerBeslutning(
                    ident = person.ident,
                    periodeId = UUID.randomUUID(),
                    handling = Handling.FRASAGT,
                    begrunnelse = "Test begrunnelse",
                )

            arbeidssøkerBeslutningRepository.lagreBeslutning(overtattArbeidssøkerBeslutning)
            arbeidssøkerBeslutningRepository.lagreBeslutning(frasagtArbeidssøkerBeslutning)

            arbeidssøkerBeslutningRepository.hentBeslutninger(person.ident) shouldHaveSize 2
            with(arbeidssøkerBeslutningRepository.hentBeslutninger(person.ident)) {
                first().periodeId shouldBe overtattArbeidssøkerBeslutning.periodeId
                last().periodeId shouldBe frasagtArbeidssøkerBeslutning.periodeId
            }
        }
    }
}
