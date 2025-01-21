package no.nav.dagpenger.rapportering.personregister.mediator.db

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.dagpenger.rapportering.personregister.mediator.db.Postgres.dataSource
import no.nav.dagpenger.rapportering.personregister.mediator.db.Postgres.withMigratedDb
import no.nav.dagpenger.rapportering.personregister.mediator.utils.MetrikkerTestUtil.actionTimer
import no.nav.dagpenger.rapportering.personregister.modell.Arbeidssøkerperiode
import no.nav.dagpenger.rapportering.personregister.modell.Person
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class PostrgesArbeidssøkerRepositoryTest {
    private val arbeidssøkerRepository = PostrgesArbeidssøkerRepository(dataSource, actionTimer)
    private val personRepository = PostgresPersonRepository(dataSource, actionTimer)

    @Test
    fun `skal lagre og hente arbeidssøkerperiode`() {
        withMigratedDb {
            personRepository.lagrePerson(Person(arbeidssøkerperiode.ident))
            arbeidssøkerRepository.lagreArbeidssøkerperiode(arbeidssøkerperiode)

            with(arbeidssøkerRepository.hentArbeidssøkerperioder(arbeidssøkerperiode.ident)) {
                size shouldBe 1
                first().periodeId shouldBe arbeidssøkerperiode.periodeId
            }
        }
    }

    @Test
    fun `lagre kaster feil hvis person ikke finnes`() {
        withMigratedDb {
            shouldThrow<IllegalStateException> {
                arbeidssøkerRepository.lagreArbeidssøkerperiode(arbeidssøkerperiode)
            }
        }
    }

    @Test
    fun `kan oppdatere overtagelse`() {
        withMigratedDb {
            personRepository.lagrePerson(Person(arbeidssøkerperiode.ident))
            arbeidssøkerRepository.lagreArbeidssøkerperiode(arbeidssøkerperiode)

            arbeidssøkerRepository.oppdaterOvertagelse(arbeidssøkerperiode.periodeId, true)

            with(arbeidssøkerRepository.hentArbeidssøkerperioder(arbeidssøkerperiode.ident)) {
                size shouldBe 1
                first().overtattBekreftelse shouldBe true
            }
        }
    }

    @Test
    fun `oppdatering av overtagelse kaster feil hvis perioden ikke finnes`() {
        withMigratedDb {
            shouldThrow<RuntimeException> {
                arbeidssøkerRepository.oppdaterOvertagelse(arbeidssøkerperiode.periodeId, true)
            }
        }
    }

    @Test
    fun `kan avslutte arbeidssøkerperiode`() {
        withMigratedDb {
            personRepository.lagrePerson(Person(arbeidssøkerperiode.ident))
            arbeidssøkerRepository.lagreArbeidssøkerperiode(arbeidssøkerperiode)

            val avsluttetDato = LocalDateTime.now()
            arbeidssøkerRepository.avsluttArbeidssøkerperiode(arbeidssøkerperiode.periodeId, avsluttetDato)

            with(arbeidssøkerRepository.hentArbeidssøkerperioder(arbeidssøkerperiode.ident)) {
                size shouldBe 1
                first().avsluttet shouldNotBe null
            }
        }
    }

    @Test
    fun `avslutt feiler hvis perioden ikke finnes`() {
        withMigratedDb {
            shouldThrow<RuntimeException> {
                arbeidssøkerRepository.avsluttArbeidssøkerperiode(
                    arbeidssøkerperiode.periodeId,
                    LocalDateTime.now(),
                )
            }
        }
    }

    @Test
    fun `kan oppdatere periodeId`() {
        withMigratedDb {
            personRepository.lagrePerson(Person(arbeidssøkerperiode.ident))
            arbeidssøkerRepository.lagreArbeidssøkerperiode(arbeidssøkerperiode)

            val nyPeriodeId = UUID.randomUUID()
            arbeidssøkerRepository.oppdaterPeriodeId(arbeidssøkerperiode.ident, arbeidssøkerperiode.periodeId, nyPeriodeId)

            with(arbeidssøkerRepository.hentArbeidssøkerperioder(arbeidssøkerperiode.ident)) {
                size shouldBe 1
                first().periodeId shouldBe nyPeriodeId
            }
        }
    }

    @Test
    fun `oppdatering av periodeId feiler hvis perioden ikke finnes`() {
        withMigratedDb {
            shouldThrow<RuntimeException> {
                arbeidssøkerRepository.oppdaterPeriodeId(arbeidssøkerperiode.ident, arbeidssøkerperiode.periodeId, UUID.randomUUID())
            }
        }
    }

    val arbeidssøkerperiode =
        Arbeidssøkerperiode(
            periodeId = UUID.randomUUID(),
            ident = "12345678901",
            startet = LocalDateTime.now().minusDays(7),
            avsluttet = null,
            overtattBekreftelse = false,
        )
}
