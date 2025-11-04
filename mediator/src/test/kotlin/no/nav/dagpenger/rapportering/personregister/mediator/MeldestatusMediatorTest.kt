package no.nav.dagpenger.rapportering.personregister.mediator

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.getunleash.FakeUnleash
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import no.nav.dagpenger.rapportering.personregister.mediator.connector.ArbeidssøkerConnector
import no.nav.dagpenger.rapportering.personregister.mediator.connector.MeldekortregisterConnector
import no.nav.dagpenger.rapportering.personregister.mediator.connector.MeldepliktConnector
import no.nav.dagpenger.rapportering.personregister.mediator.connector.PdlConnector
import no.nav.dagpenger.rapportering.personregister.mediator.db.ArbeidssøkerBeslutningRepository
import no.nav.dagpenger.rapportering.personregister.mediator.db.InMemoryPersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.service.ArbeidssøkerService
import no.nav.dagpenger.rapportering.personregister.mediator.service.PersonService
import no.nav.dagpenger.rapportering.personregister.mediator.utils.MetrikkerTestUtil.actionTimer
import no.nav.dagpenger.rapportering.personregister.mediator.utils.MetrikkerTestUtil.meldegruppeendringMetrikker
import no.nav.dagpenger.rapportering.personregister.mediator.utils.MetrikkerTestUtil.meldepliktendringMetrikker
import no.nav.dagpenger.rapportering.personregister.mediator.utils.kafka.MockKafkaProducer
import no.nav.dagpenger.rapportering.personregister.modell.AnsvarligSystem
import no.nav.dagpenger.rapportering.personregister.modell.Arbeidssøkerperiode
import no.nav.dagpenger.rapportering.personregister.modell.Ident
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.PersonObserver
import no.nav.dagpenger.rapportering.personregister.modell.Status
import no.nav.dagpenger.rapportering.personregister.modell.meldestatus.MeldestatusHendelse
import no.nav.dagpenger.rapportering.personregister.modell.meldestatus.MeldestatusResponse
import no.nav.paw.bekreftelse.paavegneav.v1.PaaVegneAv
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.TimeUnit

class MeldestatusMediatorTest {
    private lateinit var rapidsConnection: TestRapid
    private lateinit var personRepository: PersonRepository
    private lateinit var personService: PersonService
    private lateinit var arbeidssøkerConnector: ArbeidssøkerConnector
    private lateinit var overtaBekreftelseKafkaProdusent: MockKafkaProducer<PaaVegneAv>
    private lateinit var arbeidssøkerService: ArbeidssøkerService
    private lateinit var arbeidssøkerMediator: ArbeidssøkerMediator
    private lateinit var meldepliktConnector: MeldepliktConnector
    private lateinit var beslutningRepository: ArbeidssøkerBeslutningRepository
    private lateinit var beslutningObserver: BeslutningObserver
    private lateinit var meldepliktMediator: MeldepliktMediator
    private lateinit var personMediator: PersonMediator
    private lateinit var fremtidigHendelseMediator: FremtidigHendelseMediator
    private lateinit var meldestatusMediator: MeldestatusMediator

    private val pdlConnector = mockk<PdlConnector>()
    private val personObserver = mockk<PersonObserver>(relaxed = true)
    private val meldekortregisterConnector = mockk<MeldekortregisterConnector>(relaxed = true)
    private val unleash = FakeUnleash()

    @BeforeEach
    fun setup() {
        rapidsConnection = TestRapid()
        personRepository = InMemoryPersonRepository()
        personService =
            PersonService(
                pdlConnector = pdlConnector,
                personRepository = personRepository,
                personObservers = listOf(personObserver),
                cache = Caffeine.newBuilder().expireAfterWrite(1, TimeUnit.MINUTES).build(),
                meldekortregisterConnector = meldekortregisterConnector,
            )
        arbeidssøkerConnector = mockk<ArbeidssøkerConnector>(relaxed = true)
        overtaBekreftelseKafkaProdusent = MockKafkaProducer()
        arbeidssøkerService = ArbeidssøkerService(arbeidssøkerConnector)
        arbeidssøkerMediator =
            ArbeidssøkerMediator(
                arbeidssøkerService,
                personRepository,
                personService,
                listOf(personObserver),
                actionTimer,
            )
        meldepliktConnector = mockk<MeldepliktConnector>(relaxed = true)
        beslutningRepository = ArbeidssøkerBeslutningRepositoryFaker()
        beslutningObserver = BeslutningObserver(beslutningRepository)

        meldepliktMediator =
            MeldepliktMediator(
                personRepository,
                personService,
                listOf(personObserver),
                meldepliktConnector,
                actionTimer,
            )

        personMediator =
            PersonMediator(
                personRepository,
                personService,
                arbeidssøkerMediator,
                listOf(personObserver, beslutningObserver),
                meldepliktMediator,
                actionTimer,
                unleash,
            )
        fremtidigHendelseMediator =
            FremtidigHendelseMediator(
                personRepository,
                actionTimer,
            )

        meldestatusMediator =
            MeldestatusMediator(
                personRepository,
                personService,
                meldepliktConnector,
                meldepliktMediator,
                personMediator,
                fremtidigHendelseMediator,
                meldepliktendringMetrikker,
                meldegruppeendringMetrikker,
                actionTimer,
            )

        every { pdlConnector.hentIdenter(ident) } returns
            listOf(
                Ident(
                    ident,
                    Ident.IdentGruppe.FOLKEREGISTERIDENT,
                    false,
                ),
            )
    }

    private val ident = "12345678910"
    private val nå = LocalDateTime.now()
    private val tidligere = nå.minusDays(1)
    private val periodeId = UUID.randomUUID()

    @Test
    fun `meldestatusHendelse for arbeidssøker`() {
        val arenaPersonId = 2L
        val meldestatusResponse = meldestatusResponse(arenaPersonId)
        coEvery { meldepliktConnector.hentMeldestatus(eq(arenaPersonId), any(), any()) } returns meldestatusResponse

        arbeidssøker {
            meldestatusMediator.behandle(meldestatusHendelse(arenaPersonId))
        }

        with(personRepository.hentPerson(ident)!!) {
            status shouldBe Status.DAGPENGERBRUKER
            meldeplikt shouldBe true
            meldegruppe shouldBe "DAGP"
            hendelser.size shouldBe 2

            personObserver skalHaSendtOvertakelseFor this.copy(versjon = 2)
        }

        personRepository.hentAntallFremtidigeHendelser() shouldBe 2
    }

    @Test
    fun `meldestatusHendelse for arbeidssøker med ansvarligSystem DP tas ikke til følge`() {
        val arenaPersonId = 2L
        val meldestatusResponse = meldestatusResponse(arenaPersonId)
        coEvery { meldepliktConnector.hentMeldestatus(eq(arenaPersonId), any(), any()) } returns meldestatusResponse

        arbeidssøker(ansvarligSystem = AnsvarligSystem.DP) {
            meldestatusMediator.behandle(meldestatusHendelse(arenaPersonId))
        }

        with(personRepository.hentPerson(ident)!!) {
            status shouldBe Status.IKKE_DAGPENGERBRUKER
            ansvarligSystem shouldBe AnsvarligSystem.DP
            meldeplikt shouldBe false
            meldegruppe shouldBe null
            hendelser.size shouldBe 0

            personObserver skalIkkeHaSendtOvertakelseFor this.copy(versjon = 2)
        }

        personRepository.hentAntallFremtidigeHendelser() shouldBe 0
    }

    @Test
    fun `meldestatusHendelse for ikke arbeidssøker`() {
        val arenaPersonId = 3L
        val meldestatusResponse = meldestatusResponse(arenaPersonId)
        coEvery { meldepliktConnector.hentMeldestatus(eq(arenaPersonId), any(), any()) } returns meldestatusResponse

        val person = Person(ident = ident)
        personRepository.lagrePerson(person)

        meldestatusMediator.behandle(meldestatusHendelse(arenaPersonId))

        with(personRepository.hentPerson(ident)!!) {
            status shouldBe Status.IKKE_DAGPENGERBRUKER
            meldeplikt shouldBe true
            meldegruppe shouldBe "DAGP"
            hendelser.size shouldBe 2

            personObserver skalIkkeHaSendtOvertakelseFor this.copy(versjon = 2)
        }

        personRepository.hentAntallFremtidigeHendelser() shouldBe 2
    }

    @Test
    fun `meldestatusHendelse med fremtidige meldeplikt = false og DAGP for arbeidssøker`() {
        val arenaPersonId = 4L
        val meldestatusResponse = meldestatusResponse(arenaPersonId, medGjeldende = false)
        coEvery { meldepliktConnector.hentMeldestatus(eq(arenaPersonId), any(), any()) } returns meldestatusResponse

        arbeidssøker {
            meldestatusMediator.behandle(meldestatusHendelse(arenaPersonId))
        }

        with(personRepository.hentPerson(ident)!!) {
            status shouldBe Status.IKKE_DAGPENGERBRUKER
            meldeplikt shouldBe false
            meldegruppe shouldBe null
            hendelser.size shouldBe 0

            personObserver skalIkkeHaSendtOvertakelseFor this.copy(versjon = 2)
        }

        // Kun AnnenMeldegruppe fremtidig hendelse opprettes
        // Bruker har meldeplikt=false og fremtidig meldeplikt er også false og derfor skal ikke fremtidig meldeplikt hendelse opprettes
        personRepository.hentAntallFremtidigeHendelser() shouldBe 1
    }

    private fun meldestatusResponse(
        arenaPersonId: Long,
        medGjeldende: Boolean = true,
    ): MeldestatusResponse {
        val meldepliktListe = mutableListOf<MeldestatusResponse.Meldeplikt>()
        if (medGjeldende) {
            meldepliktListe.add(
                MeldestatusResponse.Meldeplikt(
                    meldeplikt = true,
                    meldepliktperiode =
                        MeldestatusResponse.Periode(
                            fom = nå,
                            tom = nå.plusDays(13),
                        ),
                    begrunnelse = "Vedtak",
                    endringsdata =
                        MeldestatusResponse.Endring(
                            registrertAv = "R123456",
                            registreringsdato = nå,
                            endretAv = "E123456",
                            endringsdato = nå,
                        ),
                ),
            )
        }

        meldepliktListe.add(
            MeldestatusResponse.Meldeplikt(
                meldeplikt = false,
                meldepliktperiode =
                    MeldestatusResponse.Periode(
                        fom = nå.plusDays(14),
                        tom = null,
                    ),
                begrunnelse = "Vedtak",
                endringsdata =
                    MeldestatusResponse.Endring(
                        registrertAv = "R123456",
                        registreringsdato = nå,
                        endretAv = "E123456",
                        endringsdato = nå,
                    ),
            ),
        )

        val meldegruppeListe = mutableListOf<MeldestatusResponse.Meldegruppe>()
        if (medGjeldende) {
            meldegruppeListe.add(
                MeldestatusResponse.Meldegruppe(
                    meldegruppe = "DAGP",
                    meldegruppeperiode = null,
                    begrunnelse = "Vedtak",
                    endringsdata = null,
                ),
            )
        }

        meldegruppeListe.add(
            MeldestatusResponse.Meldegruppe(
                meldegruppe = "ARBS",
                meldegruppeperiode =
                    MeldestatusResponse.Periode(
                        fom = nå.plusDays(14),
                        tom = null,
                    ),
                begrunnelse = "Vedtak",
                endringsdata =
                    MeldestatusResponse.Endring(
                        registrertAv = "R123456",
                        registreringsdato = nå,
                        endretAv = "E123456",
                        endringsdato = nå,
                    ),
            ),
        )

        return MeldestatusResponse(
            arenaPersonId = arenaPersonId,
            personIdent = ident,
            formidlingsgruppe = "DAGP",
            harMeldtSeg = true,
            meldepliktListe = meldepliktListe,
            meldegruppeListe = meldegruppeListe,
        )
    }

    private fun arbeidssøker(
        overtattBekreftelse: Boolean = false,
        ansvarligSystem: AnsvarligSystem = AnsvarligSystem.ARENA,
        block: Person.() -> Unit,
    ) {
        val person =
            Person(
                ident = ident,
                arbeidssøkerperioder =
                    mutableListOf(
                        Arbeidssøkerperiode(
                            periodeId,
                            ident,
                            tidligere,
                            null,
                            overtattBekreftelse,
                        ),
                    ),
            ).apply { setAnsvarligSystem(ansvarligSystem) }
        personRepository.lagrePerson(person)
        person.apply(block)
    }

    private fun meldestatusHendelse(
        personId: Long = 1L,
        meldestatusId: Long = 2L,
        hendelseId: Long = 3L,
    ) = MeldestatusHendelse(
        personId = personId,
        meldestatusId = meldestatusId,
        hendelseId = hendelseId,
    )
}
