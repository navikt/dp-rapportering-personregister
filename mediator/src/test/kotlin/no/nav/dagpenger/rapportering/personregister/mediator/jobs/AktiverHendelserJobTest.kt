package no.nav.dagpenger.rapportering.personregister.mediator.jobs

import io.kotest.matchers.shouldBe
import io.mockk.mockk
import no.nav.dagpenger.rapportering.personregister.mediator.ArbeidssøkerMediator
import no.nav.dagpenger.rapportering.personregister.mediator.PersonMediator
import no.nav.dagpenger.rapportering.personregister.mediator.api.ApiTestSetup
import no.nav.dagpenger.rapportering.personregister.mediator.db.Postgres.dataSource
import no.nav.dagpenger.rapportering.personregister.mediator.db.PostgresPersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.service.ArbeidssøkerService
import no.nav.dagpenger.rapportering.personregister.mediator.utils.MetrikkerTestUtil.actionTimer
import no.nav.dagpenger.rapportering.personregister.modell.DagpengerMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.MeldepliktHendelse
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.PersonObserver
import no.nav.dagpenger.rapportering.personregister.modell.StartetArbeidssøkerperiodeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.erArbeidssøker
import no.nav.dagpenger.rapportering.personregister.modell.oppfyllerKrav
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class AktiverHendelserJobTest : ApiTestSetup() {
    private val arbeidssøkerService = mockk<ArbeidssøkerService>()
    private val personObserver = mockk<PersonObserver>(relaxed = true)
    private var personRepository = PostgresPersonRepository(dataSource, actionTimer)
    private val arbeidssøkerMediator = ArbeidssøkerMediator(arbeidssøkerService, personRepository, listOf(personObserver), actionTimer)
    private val personMediator = PersonMediator(personRepository, arbeidssøkerMediator, listOf(personObserver), actionTimer)

    private val ident = "12345678910"

    @Test
    fun `aktiverHendelser aktiverer hendelser og sletter dem fra tabellen`() {
        setUpTestApplication {
            val aktiverHendelserJob = AktiverHendelserJob(client)
            val nå = LocalDateTime.now()
            val person =
                Person(ident = ident).apply {
                    behandle(
                        StartetArbeidssøkerperiodeHendelse(
                            periodeId = UUID.randomUUID(),
                            ident = ident,
                            startet = nå.minusDays(1),
                        ),
                    )
                }
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
                    fristBrutt = false,
                )

            personRepository.lagrePerson(person)
            personRepository.lagreFremtidigHendelse(meldegruppeHendelse)
            personRepository.lagreFremtidigHendelse(meldepliktHendelse)

            with(personRepository.hentPerson(ident)!!) {
                hendelser.size shouldBe 1
                erArbeidssøker shouldBe true
                oppfyllerKrav shouldBe false
            }

            val antallHendelserAktivert = aktiverHendelserJob.aktivererHendelser(personRepository, personMediator)
            antallHendelserAktivert shouldBe 2
            personRepository.hentHendelserSomSkalAktiveres().size shouldBe 0
            with(personRepository.hentPerson(ident)!!) {
                hendelser.size shouldBe 3
                erArbeidssøker shouldBe true
                oppfyllerKrav shouldBe true
            }
        }
    }
}
