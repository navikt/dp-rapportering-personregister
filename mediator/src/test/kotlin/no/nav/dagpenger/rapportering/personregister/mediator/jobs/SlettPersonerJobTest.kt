package no.nav.dagpenger.rapportering.personregister.mediator.jobs

import io.kotest.matchers.shouldBe
import no.nav.dagpenger.rapportering.personregister.mediator.api.ApiTestSetup
import no.nav.dagpenger.rapportering.personregister.mediator.db.Postgres.dataSource
import no.nav.dagpenger.rapportering.personregister.mediator.db.PostgresPersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.utils.MetrikkerTestUtil.actionTimer
import no.nav.dagpenger.rapportering.personregister.modell.AnnenMeldegruppeHendelse
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
    private val ident5 = "12345678915"
    private val ident6 = "12345678916"

    @Test
    fun `jobben sletter personer og tilhørende data`() {
        setUpTestApplication {
            val slettPersonerJob = SlettPersonerJob(client)
            val nå = LocalDateTime.now()

            // Disse skal ikke slettes:
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
            val fremtidigHendelse2 =
                MeldepliktHendelse(
                    ident = ident2,
                    referanseId = "222",
                    dato = nå,
                    startDato = nå.plusDays(1),
                    sluttDato = null,
                    statusMeldeplikt = true,
                    harMeldtSeg = true,
                )

            // Disse skal slettes:
            // Har ikke hendelser
            val person3 = Person(ident = ident3)

            // Har en gammel hendelse
            val person4 =
                Person(ident = ident4).apply {
                    behandle(
                        MeldepliktHendelse(
                            ident = ident2,
                            referanseId = "222",
                            dato = nå.minusDays(61),
                            startDato = nå.minusDays(61),
                            sluttDato = nå.minusDays(61),
                            statusMeldeplikt = true,
                            harMeldtSeg = true,
                        )
                    )
                }

            // Har en fremtidig hendelse med statusMeldeplikt = false
            val person5 = Person(ident = ident5)
            val fremtidigHendelse5 =
                MeldepliktHendelse(
                    ident = ident5,
                    referanseId = "555",
                    dato = nå,
                    startDato = nå.plusDays(2),
                    sluttDato = null,
                    statusMeldeplikt = false,
                    harMeldtSeg = true,
                )

            // Har en fremtidig hendelse med meldegruppeKode = ARBS
            val person6 = Person(ident = ident6)
            val fremtidigHendelse6 =
                AnnenMeldegruppeHendelse(
                    ident = ident6,
                    referanseId = "666",
                    dato = nå,
                    startDato = nå.plusDays(3),
                    sluttDato = null,
                    meldegruppeKode = "ARBS",
                    harMeldtSeg = false,
                )

            personRepository.lagrePerson(person1)
            personRepository.lagrePerson(person2)
            personRepository.lagrePerson(person3)
            personRepository.lagrePerson(person4)
            personRepository.lagrePerson(person5)
            personRepository.lagrePerson(person6)

            personRepository.lagreFremtidigHendelse(fremtidigHendelse2)
            personRepository.lagreFremtidigHendelse(fremtidigHendelse5)
            personRepository.lagreFremtidigHendelse(fremtidigHendelse6)

            // Sjekker før
            personRepository.hentAntallHendelser() shouldBe 2
            personRepository.hentAntallFremtidigeHendelser() shouldBe 3
            personRepository.hentPersonerSomKanSlettes().size shouldBe 4

            // Sletter
            slettPersonerJob.slettPersoner(personRepository) shouldBe 4

            // Sjekker etter
            personRepository.hentAntallHendelser() shouldBe 1
            personRepository.hentAntallFremtidigeHendelser() shouldBe 3
            personRepository.hentPersonerSomKanSlettes().size shouldBe 0
        }
    }
}
