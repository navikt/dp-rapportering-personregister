package no.nav.dagpenger.rapportering.personregister.mediator.jobs

import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode.Companion.OK
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import no.nav.dagpenger.rapportering.personregister.mediator.ArbeidssøkerMediator
import no.nav.dagpenger.rapportering.personregister.mediator.FremtidigHendelseMediator
import no.nav.dagpenger.rapportering.personregister.mediator.MeldepliktMediator
import no.nav.dagpenger.rapportering.personregister.mediator.MeldestatusMediator
import no.nav.dagpenger.rapportering.personregister.mediator.PersonMediator
import no.nav.dagpenger.rapportering.personregister.mediator.api.ApiTestSetup
import no.nav.dagpenger.rapportering.personregister.mediator.connector.MeldepliktConnector
import no.nav.dagpenger.rapportering.personregister.mediator.connector.createMockClient
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepositoryPostgres
import no.nav.dagpenger.rapportering.personregister.mediator.db.Postgres.dataSource
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.JobbkjøringMetrikker
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.MeldegruppeendringMetrikker
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.MeldepliktendringMetrikker
import no.nav.dagpenger.rapportering.personregister.mediator.service.ArbeidssøkerService
import no.nav.dagpenger.rapportering.personregister.mediator.service.PersonService
import no.nav.dagpenger.rapportering.personregister.mediator.utils.MetrikkerTestUtil.actionTimer
import no.nav.dagpenger.rapportering.personregister.mediator.utils.UUIDv7
import no.nav.dagpenger.rapportering.personregister.modell.Ident
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.erArbeidssøker
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.DagpengerMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.MeldepliktHendelse
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.StartetArbeidssøkerperiodeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.VedtakHendelse
import no.nav.dagpenger.rapportering.personregister.modell.meldestatus.MeldestatusResponse
import no.nav.dagpenger.rapportering.personregister.modell.oppfyllerKrav
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class AktiverHendelserJobTest : ApiTestSetup() {
    private val jobbkjøringMetrikker = mockk<JobbkjøringMetrikker>(relaxed = true)
    private val arbeidssøkerService = mockk<ArbeidssøkerService>()
    private var personRepository = PersonRepositoryPostgres(dataSource, actionTimer)
    private val personService =
        PersonService(
            pdlConnector = pdlConnector,
            personRepository = personRepository,
            personObservers = listOf(personObserver),
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

            val aktiverHendelserJob =
                AktiverHendelserJob(
                    personRepository,
                    personService,
                    personMediator,
                    meldestatusMediator,
                    meldepliktConnector,
                    client,
                    jobbkjøringMetrikker,
                )
            val nå = LocalDateTime.now()

            val person1 =
                Person(ident = ident1).apply {
                    behandle(
                        StartetArbeidssøkerperiodeHendelse(
                            periodeId = UUIDv7.newUuid(),
                            ident = ident1,
                            startDato = nå.minusDays(1),
                        ),
                    )
                }
            val person2 =
                Person(ident = ident2).apply {
                    behandle(
                        StartetArbeidssøkerperiodeHendelse(
                            periodeId = UUIDv7.newUuid(),
                            ident = ident2,
                            startDato = nå.minusDays(1),
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

            personRepository.hentHendelserSomSkalAktiveres().size shouldBe 3
            aktiverHendelserJob.execute()
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

            verify(exactly = 1) { jobbkjøringMetrikker.jobbSjekketOmDenSkulleKjøre() }
            verify(exactly = 1) { jobbkjøringMetrikker.jobbFullfort(duration = any(), affectedRows = 3) }
            verify(exactly = 0) { jobbkjøringMetrikker.jobbFeilet() }
        }
    }

    @Test
    fun `aktiverHendelser stopper videre behandling for ident ved feil og beholder fremtidige hendelser`() {
        val personRepositoryMock = mockk<PersonRepository>()
        val personServiceMock = mockk<PersonService>()
        val personMediatorMock = mockk<PersonMediator>()
        val meldestatusMediatorMock = mockk<MeldestatusMediator>(relaxed = true)
        val meldepliktConnectorMock =
            mockk<MeldepliktConnector>(relaxed = true)

        val nå = LocalDateTime.now()
        val behandlet =
            VedtakHendelse(
                ident = ident1,
                referanseId = "behandlet-1",
                startDato = nå.minusDays(2),
                utfall = true,
            )
        val feiler =
            VedtakHendelse(
                ident = ident1,
                referanseId = "feiler-2",
                startDato = nå.minusDays(1),
                utfall = true,
            )
        val ikkeBehandlet =
            VedtakHendelse(
                ident = ident1,
                referanseId = "ikke-behandlet-3",
                startDato = nå,
                utfall = true,
            )

        every { personRepositoryMock.hentHendelserSomSkalAktiveres() } returns listOf(behandlet, feiler, ikkeBehandlet)
        every { personRepositoryMock.slettFremtidigHendelse(any()) } just Runs
        every { personServiceMock.hentPerson(ident1) } returns Person(ident1)
        every { personMediatorMock.behandle(any<VedtakHendelse>()) } answers {
            if (firstArg<VedtakHendelse>() == feiler) throw RuntimeException("boom")
        }

        val aktiverHendelserJob =
            AktiverHendelserJob(
                personRepositoryMock,
                personServiceMock,
                personMediatorMock,
                meldestatusMediatorMock,
                meldepliktConnectorMock,
                httpClient = createMockClient(OK.value, ""),
                jobbkjøringMetrikker = jobbkjøringMetrikker,
            )

        aktiverHendelserJob.execute()

        verify(exactly = 1) { personMediatorMock.behandle(behandlet) }
        verify(exactly = 1) { personMediatorMock.behandle(feiler) }
        verify(exactly = 2) { personMediatorMock.behandle(any<VedtakHendelse>()) }

        verify(exactly = 1) { personRepositoryMock.slettFremtidigHendelse(behandlet.referanseId) }
        verify(exactly = 0) { personRepositoryMock.slettFremtidigHendelse(feiler.referanseId) }
        verify(exactly = 0) { personRepositoryMock.slettFremtidigHendelse(ikkeBehandlet.referanseId) }
    }

    @Test
    fun `aktiverHendelser fortsetter med andre identer selv om en ident feiler`() {
        val personRepositoryMock = mockk<PersonRepository>()
        val personServiceMock = mockk<PersonService>()
        val personMediatorMock = mockk<PersonMediator>()
        val meldestatusMediatorMock = mockk<MeldestatusMediator>(relaxed = true)
        val meldepliktConnectorMock =
            mockk<MeldepliktConnector>(relaxed = true)

        val nå = LocalDateTime.now()
        val vedtakSomFeiler =
            VedtakHendelse(
                ident = ident1,
                referanseId = "ident1-feiler-1",
                startDato = nå.minusDays(1),
                utfall = true,
            )
        val meldegruppeForIdent2 =
            DagpengerMeldegruppeHendelse(
                ident = ident2,
                referanseId = "ident2-behandles-1",
                dato = nå.minusDays(1),
                startDato = nå,
                sluttDato = null,
                meldegruppeKode = "DAGP",
                harMeldtSeg = true,
            )

        every { personRepositoryMock.hentHendelserSomSkalAktiveres() } returns listOf(vedtakSomFeiler, meldegruppeForIdent2)
        every { personRepositoryMock.slettFremtidigHendelse(any()) } just Runs
        every { personServiceMock.hentPerson(ident1) } returns Person(ident1)
        every { personServiceMock.hentPerson(ident2) } returns Person(ident2)
        every { personMediatorMock.behandle(vedtakSomFeiler) } throws RuntimeException("boom")

        coEvery { meldepliktConnectorMock.hentMeldestatus(ident = ident2) } returns
            MeldestatusResponse(
                arenaPersonId = 2L,
                personIdent = ident2,
                formidlingsgruppe = "Formidling",
                harMeldtSeg = false,
                meldepliktListe = listOf(),
                meldegruppeListe = listOf(),
            )

        val aktiverHendelserJob =
            AktiverHendelserJob(
                personRepositoryMock,
                personServiceMock,
                personMediatorMock,
                meldestatusMediatorMock,
                meldepliktConnectorMock,
                httpClient = createMockClient(OK.value, ""),
                jobbkjøringMetrikker = jobbkjøringMetrikker,
            )

        aktiverHendelserJob.execute()

        verify(exactly = 1) { personMediatorMock.behandle(vedtakSomFeiler) }
        verify(exactly = 1) {
            meldestatusMediatorMock.behandleHendelse(
                meldestatusId = "ident2-behandles-1",
                person = any(),
                meldestatus = any(),
            )
        }

        verify(exactly = 0) { personRepositoryMock.slettFremtidigHendelse("ident1-feiler-1") }
        verify(exactly = 1) { personRepositoryMock.slettFremtidigHendelse("ident2-behandles-1") }

        coVerify(exactly = 1) { meldepliktConnectorMock.hentMeldestatus(any(), eq(ident2), any()) }
    }

    @Test
    fun `execute inkrementerer metrikker hvis jobben feiler`() {
        val personRepositoryMock = mockk<PersonRepository> {}
        every { personRepositoryMock.hentHendelserSomSkalAktiveres() } throws RuntimeException()

        val aktiverHendelserJob =
            AktiverHendelserJob(
                personRepositoryMock,
                personService,
                personMediator,
                meldestatusMediator,
                meldepliktConnector,
                httpClient = createMockClient(OK.value, ""),
                jobbkjøringMetrikker = jobbkjøringMetrikker,
            )

        aktiverHendelserJob.execute()

        verify(exactly = 1) { jobbkjøringMetrikker.jobbSjekketOmDenSkulleKjøre() }
        verify(exactly = 0) { jobbkjøringMetrikker.jobbFullfort(duration = any(), affectedRows = any()) }
        verify(exactly = 1) { jobbkjøringMetrikker.jobbFeilet() }
    }
}
