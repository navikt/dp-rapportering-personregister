package no.nav.dagpenger.rapportering.personregister.mediator.jobs

import io.kotest.matchers.shouldBe
import no.nav.dagpenger.rapportering.personregister.mediator.api.ApiTestSetup
import no.nav.dagpenger.rapportering.personregister.mediator.db.Postgres.dataSource
import no.nav.dagpenger.rapportering.personregister.mediator.db.PostgresPersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.utils.MetrikkerTestUtil.actionTimer
import no.nav.dagpenger.rapportering.personregister.modell.MeldepliktHendelse
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.StartetArbeidssøkerperiodeHendelse
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class SlettPersonerJobTest : ApiTestSetup() {
    private var personRepository = PostgresPersonRepository(dataSource, actionTimer)

    private val ident1 = "12345678911"
    private val ident2 = "12345678912"
    private val ident3 = "12345678913"
    private val ident4 = "12345678914"

    @Test
    fun `jobben sletter personer og tilhørende data`() {
        setUpTestApplication {
            val slettPersonerJob = SlettPersonerJob(client)
            val nå = LocalDateTime.now()

            // Har en hendelse
            val person1 =
                Person(ident = ident1).apply {
                    behandle(
                        StartetArbeidssøkerperiodeHendelse(
                            periodeId = UUID.randomUUID(),
                            ident = ident1,
                            startet = nå.minusDays(1),
                        ),
                    )
                }

            // Har en fremtidig hendelse
            val person2 = Person(ident = ident2)
            val meldepliktHendelse2 =
                MeldepliktHendelse(
                    ident = ident2,
                    referanseId = "123",
                    dato = nå.plusDays(1),
                    startDato = nå,
                    sluttDato = null,
                    statusMeldeplikt = true,
                )

            // Har ikke hendelser
            val person3 = Person(ident = ident3)

            // Har en gammel hendelse
            val person4 =
                Person(ident = ident4).apply {
                    behandle(
                        StartetArbeidssøkerperiodeHendelse(
                            periodeId = UUID.randomUUID(),
                            ident = ident4,
                            startet = nå.minusDays(61),
                        ),
                    )
                }

            personRepository.lagrePerson(person1)
            personRepository.lagrePerson(person2)
            personRepository.lagrePerson(person3)
            personRepository.lagrePerson(person4)

            personRepository.lagreFremtidigHendelse(meldepliktHendelse2)

            // Sjekker før
            personRepository.hentAntallHendelser() shouldBe 2
            personRepository.hentAntallFremtidigeHendelser() shouldBe 1
            personRepository.hentPersonerSomKanSlettes().size shouldBe 2

            // Sletter
            slettPersonerJob.slettPersoner(personRepository) shouldBe 2

            // Sjekker etter
            personRepository.hentAntallHendelser() shouldBe 1
            personRepository.hentAntallFremtidigeHendelser() shouldBe 1
            personRepository.hentPersonerSomKanSlettes().size shouldBe 0
        }
    }
}
