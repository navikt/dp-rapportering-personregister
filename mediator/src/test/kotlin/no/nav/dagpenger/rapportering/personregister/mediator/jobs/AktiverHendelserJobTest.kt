package no.nav.dagpenger.rapportering.personregister.mediator.jobs

import com.github.benmanes.caffeine.cache.Caffeine
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import no.nav.dagpenger.rapportering.personregister.mediator.ArbeidssøkerMediator
import no.nav.dagpenger.rapportering.personregister.mediator.FremtidigHendelseMediator
import no.nav.dagpenger.rapportering.personregister.mediator.MeldepliktMediator
import no.nav.dagpenger.rapportering.personregister.mediator.MeldestatusMediator
import no.nav.dagpenger.rapportering.personregister.mediator.PersonMediator
import no.nav.dagpenger.rapportering.personregister.mediator.api.ApiTestSetup
import no.nav.dagpenger.rapportering.personregister.mediator.db.Postgres.dataSource
import no.nav.dagpenger.rapportering.personregister.mediator.db.PostgresPersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.MeldegruppeendringMetrikker
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.MeldepliktendringMetrikker
import no.nav.dagpenger.rapportering.personregister.mediator.service.ArbeidssøkerService
import no.nav.dagpenger.rapportering.personregister.mediator.service.PersonService
import no.nav.dagpenger.rapportering.personregister.mediator.utils.MetrikkerTestUtil.actionTimer
import no.nav.dagpenger.rapportering.personregister.modell.Ident
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.erArbeidssøker
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.DagpengerMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.MeldepliktHendelse
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.StartetArbeidssøkerperiodeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.meldestatus.MeldestatusResponse
import no.nav.dagpenger.rapportering.personregister.modell.oppfyllerKrav
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class AktiverHendelserJobTest : ApiTestSetup() {
    private val arbeidssøkerService = mockk<ArbeidssøkerService>()
    private var personRepository = PostgresPersonRepository(dataSource, actionTimer)
    private val personService =
        PersonService(
            pdlConnector = pdlConnector,
            personRepository = personRepository,
            personObservers = listOf(personObserver),
            cache = Caffeine.newBuilder().build(),
            meldekortregisterConnector = meldekortregisterConnector,
        )
    private val arbeidssøkerMediator =
        ArbeidssøkerMediator(arbeidssøkerService, personRepository, personService, listOf(personObserver), actionTimer)
    private val meldepliktMediator =
        MeldepliktMediator(personRepository, personService, listOf(personObserver), meldepliktConnector, actionTimer)
    private val personMediator =
        PersonMediator(
            personRepository,
            personService,
            arbeidssøkerMediator,
            listOf(personObserver),
            meldepliktMediator,
            actionTimer,
            unleash,
        )
    private val fremtidigHendelseMediator = FremtidigHendelseMediator(personRepository, actionTimer)
    private val meldestatusMediator =
        MeldestatusMediator(
            personRepository = personRepository,
            personService = personService,
            meldepliktConnector = meldepliktConnector,
            meldepliktMediator = meldepliktMediator,
            personMediator = personMediator,
            fremtidigHendelseMediator = fremtidigHendelseMediator,
            meldepliktendringMetrikker = mockk<MeldepliktendringMetrikker>(relaxed = true),
            meldegruppeendringMetrikker = mockk<MeldegruppeendringMetrikker>(relaxed = true),
            actionTimer = actionTimer,
        )

    private val ident1 = "12345678911"
    private val ident2 = "12345678912"

    @Test
    fun `aktiverHendelser aktiverer hendelser og sletter dem fra tabellen`() {
        setUpTestApplication {
            every { pdlConnector.hentIdenter(ident1) } returns
                listOf(
                    Ident(
                        ident1,
                        Ident.IdentGruppe.FOLKEREGISTERIDENT,
                        false,
                    ),
                )
            every { pdlConnector.hentIdenter(ident2) } returns
                listOf(
                    Ident(
                        ident2,
                        Ident.IdentGruppe.FOLKEREGISTERIDENT,
                        false,
                    ),
                )

            val aktiverHendelserJob = AktiverHendelserJob(client)
            val nå = LocalDateTime.now()

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
            val person2 =
                Person(ident = ident2).apply {
                    behandle(
                        StartetArbeidssøkerperiodeHendelse(
                            periodeId = UUID.randomUUID(),
                            ident = ident2,
                            startet = nå.minusDays(1),
                        ),
                    )
                }

            val meldepliktHendelse =
                MeldepliktHendelse(
                    ident = ident1,
                    referanseId = "123",
                    dato = nå.minusDays(2),
                    startDato = nå,
                    sluttDato = null,
                    statusMeldeplikt = true,
                    harMeldtSeg = true,
                )
            val meldegruppeHendelse =
                DagpengerMeldegruppeHendelse(
                    ident = ident1,
                    referanseId = "321",
                    dato = nå.minusDays(1),
                    startDato = nå,
                    sluttDato = null,
                    meldegruppeKode = "DAGP",
                    harMeldtSeg = false,
                )

            val meldegruppeHendelse2 =
                DagpengerMeldegruppeHendelse(
                    ident = ident2,
                    referanseId = "322",
                    dato = nå.minusDays(1),
                    startDato = nå,
                    sluttDato = null,
                    meldegruppeKode = "DAGP",
                    harMeldtSeg = true,
                )

            personRepository.lagrePerson(person1)
            personRepository.lagreFremtidigHendelse(meldegruppeHendelse)
            personRepository.lagreFremtidigHendelse(meldepliktHendelse)

            personRepository.lagrePerson(person2)
            personRepository.lagreFremtidigHendelse(meldegruppeHendelse2)

            coEvery { meldepliktConnector.hentMeldestatus(ident = person1.ident) } returns
                MeldestatusResponse(
                    arenaPersonId = 1L,
                    personIdent = person1.ident,
                    formidlingsgruppe = "Formidling",
                    harMeldtSeg = false,
                    meldepliktListe =
                        listOf(
                            MeldestatusResponse.Meldeplikt(
                                meldeplikt = meldepliktHendelse.statusMeldeplikt,
                                meldepliktperiode =
                                    MeldestatusResponse.Periode(
                                        LocalDateTime.now().minusDays(2),
                                    ),
                                begrunnelse = null,
                                endringsdata =
                                    MeldestatusResponse.Endring(
                                        "R123456",
                                        LocalDateTime.now().minusDays(7),
                                        "E654321",
                                        LocalDateTime.now(),
                                    ),
                            ),
                        ),
                    meldegruppeListe =
                        listOf(
                            MeldestatusResponse.Meldegruppe(
                                meldegruppe = meldegruppeHendelse.meldegruppeKode,
                                meldegruppeperiode =
                                    MeldestatusResponse.Periode(
                                        LocalDateTime.now(),
                                    ),
                                begrunnelse = null,
                                endringsdata =
                                    MeldestatusResponse.Endring(
                                        "R123456",
                                        LocalDateTime.now().minusDays(7),
                                        "E654321",
                                        LocalDateTime.now(),
                                    ),
                            ),
                        ),
                )

            coEvery { meldepliktConnector.hentMeldestatus(ident = person2.ident) } returns
                MeldestatusResponse(
                    arenaPersonId = 2L,
                    personIdent = person2.ident,
                    formidlingsgruppe = "Formidling",
                    harMeldtSeg = false,
                    meldepliktListe = listOf(),
                    meldegruppeListe =
                        listOf(
                            MeldestatusResponse.Meldegruppe(
                                meldegruppe = meldegruppeHendelse.meldegruppeKode,
                                meldegruppeperiode =
                                    MeldestatusResponse.Periode(
                                        LocalDateTime.now().minusDays(1),
                                    ),
                                begrunnelse = null,
                                endringsdata = null,
                            ),
                        ),
                )

            with(personRepository.hentPerson(ident1)!!) {
                hendelser.size shouldBe 1
                erArbeidssøker shouldBe true
                oppfyllerKrav shouldBe false
            }

            with(personRepository.hentPerson(ident2)!!) {
                hendelser.size shouldBe 1
                erArbeidssøker shouldBe true
                oppfyllerKrav shouldBe false
            }

            val antallHendelserAktivert =
                aktiverHendelserJob.aktivererHendelser(
                    personRepository,
                    personService,
                    personMediator,
                    meldestatusMediator,
                    meldepliktConnector,
                )
            antallHendelserAktivert shouldBe 3
            personRepository.hentHendelserSomSkalAktiveres().size shouldBe 0

            with(personRepository.hentPerson(ident1)!!) {
                hendelser.size shouldBe 3
                (hendelser[1] as MeldepliktHendelse).harMeldtSeg shouldBe false
                (hendelser[2] as DagpengerMeldegruppeHendelse).harMeldtSeg shouldBe false
                erArbeidssøker shouldBe true
                oppfyllerKrav shouldBe true
            }
            with(personRepository.hentPerson(ident2)!!) {
                hendelser.size shouldBe 2
                (hendelser[1] as DagpengerMeldegruppeHendelse).harMeldtSeg shouldBe false
                erArbeidssøker shouldBe true
                oppfyllerKrav shouldBe false
            }

            // hentMeldestatus må kalles kun én gang for hver bruker
            coVerify { meldepliktConnector.hentMeldestatus(any(), eq(ident1), any()) }
            coVerify { meldepliktConnector.hentMeldestatus(any(), eq(ident2), any()) }
        }
    }
}
