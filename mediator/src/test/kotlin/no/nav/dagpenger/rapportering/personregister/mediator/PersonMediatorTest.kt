package no.nav.dagpenger.rapportering.personregister.mediator

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.getunleash.FakeUnleash
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.rapportering.personregister.mediator.connector.ArbeidssøkerConnector
import no.nav.dagpenger.rapportering.personregister.mediator.connector.ArbeidssøkerperiodeResponse
import no.nav.dagpenger.rapportering.personregister.mediator.connector.BrukerResponse
import no.nav.dagpenger.rapportering.personregister.mediator.connector.MeldekortregisterConnector
import no.nav.dagpenger.rapportering.personregister.mediator.connector.MeldepliktConnector
import no.nav.dagpenger.rapportering.personregister.mediator.connector.MetadataResponse
import no.nav.dagpenger.rapportering.personregister.mediator.connector.PdlConnector
import no.nav.dagpenger.rapportering.personregister.mediator.db.ArbeidssøkerBeslutningRepository
import no.nav.dagpenger.rapportering.personregister.mediator.db.InMemoryPersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.service.ArbeidssøkerService
import no.nav.dagpenger.rapportering.personregister.mediator.service.PersonService
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.ArbeidssøkerBeslutning
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.Handling
import no.nav.dagpenger.rapportering.personregister.mediator.utils.MetrikkerTestUtil.actionTimer
import no.nav.dagpenger.rapportering.personregister.mediator.utils.kafka.MockKafkaProducer
import no.nav.dagpenger.rapportering.personregister.modell.AnsvarligSystem
import no.nav.dagpenger.rapportering.personregister.modell.Arbeidssøkerperiode
import no.nav.dagpenger.rapportering.personregister.modell.Ident
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.PersonObserver
import no.nav.dagpenger.rapportering.personregister.modell.Status.DAGPENGERBRUKER
import no.nav.dagpenger.rapportering.personregister.modell.Status.IKKE_DAGPENGERBRUKER
import no.nav.dagpenger.rapportering.personregister.modell.gjeldende
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.AnnenMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.DagpengerMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.MeldepliktHendelse
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.MeldesyklusErPassertHendelse
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.NødbremsHendelse
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.SøknadHendelse
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.VedtakHendelse
import no.nav.dagpenger.rapportering.personregister.modell.merkPeriodeSomIkkeOvertatt
import no.nav.dagpenger.rapportering.personregister.modell.merkPeriodeSomOvertatt
import no.nav.dagpenger.rapportering.personregister.modell.sendStartMeldingTilMeldekortregister
import no.nav.dagpenger.rapportering.personregister.modell.sendStoppMeldingTilMeldekortregister
import no.nav.paw.bekreftelse.paavegneav.v1.PaaVegneAv
import no.nav.paw.bekreftelse.paavegneav.v1.vo.Bekreftelsesloesning
import no.nav.paw.bekreftelse.paavegneav.v1.vo.Start
import no.nav.paw.bekreftelse.paavegneav.v1.vo.Stopp
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.UUID

class PersonMediatorTest {
    private lateinit var rapidsConnection: TestRapid
    private lateinit var personRepository: PersonRepository
    private lateinit var personService: PersonService
    private lateinit var arbeidssøkerConnector: ArbeidssøkerConnector
    private lateinit var overtaBekreftelseKafkaProdusent: MockKafkaProducer<PaaVegneAv>
    private lateinit var personMediator: PersonMediator
    private lateinit var arbeidssøkerService: ArbeidssøkerService
    private lateinit var arbeidssøkerMediator: ArbeidssøkerMediator
    private lateinit var meldepliktMediator: MeldepliktMediator
    private lateinit var meldepliktConnector: MeldepliktConnector
    private lateinit var beslutningRepository: ArbeidssøkerBeslutningRepository
    private lateinit var beslutningObserver: BeslutningObserver

    private val pdlConnector = mockk<PdlConnector>()
    private val personObserver = mockk<PersonObserver>(relaxed = true)
    private val meldekortregisterConnector = mockk<MeldekortregisterConnector>(relaxed = true)

    private val unleash = FakeUnleash()

    init {
        System.setProperty("KAFKA_SCHEMA_REGISTRY", "KAFKA_SCHEMA_REGISTRY")
        System.setProperty("KAFKA_SCHEMA_REGISTRY_USER", "KAFKA_SCHEMA_REGISTRY_USER")
        System.setProperty("KAFKA_SCHEMA_REGISTRY_PASSWORD", "KAFKA_SCHEMA_REGISTRY_PASSWORD")
        System.setProperty("KAFKA_BROKERS", "KAFKA_BROKERS")
        System.setProperty("UNLEASH_SERVER_API_URL", "http://localhost")
        System.setProperty("UNLEASH_SERVER_API_TOKEN", "token")
        System.setProperty("UNLEASH_SERVER_API_ENV", "development")
    }

    @BeforeEach
    fun setup() {
        rapidsConnection = TestRapid()
        personRepository = InMemoryPersonRepository()
        beslutningRepository = ArbeidssøkerBeslutningRepositoryFaker()
        beslutningObserver = BeslutningObserver(beslutningRepository)
        personService =
            PersonService(
                pdlConnector = pdlConnector,
                personRepository = personRepository,
                personObservers = listOf(personObserver, beslutningObserver),
                cache = Caffeine.newBuilder().build(),
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
                listOf(personObserver, beslutningObserver),
                actionTimer,
            )
        meldepliktConnector = mockk<MeldepliktConnector>(relaxed = true)
        meldepliktMediator =
            MeldepliktMediator(
                personRepository,
                personService,
                listOf(personObserver, beslutningObserver),
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

        unleash.enableAll()
        every { pdlConnector.hentIdenter(ident) } returns listOf(Ident(ident, Ident.IdentGruppe.FOLKEREGISTERIDENT, false))
    }

    private val ident = "12345678910"
    private val nå = LocalDateTime.now()
    private val tidligere = nå.minusDays(1)
    private val periodeId = UUID.randomUUID()

    @Nested
    inner class SøknadHendelser {
        @Test
        fun `søknad for ny person`() {
            coEvery { arbeidssøkerConnector.hentSisteArbeidssøkerperiode(any()) } returns emptyList()
            personMediator.behandle(søknadHendelse(ident))

            personRepository
                .hentPerson(ident)
                ?.apply {
                    ident shouldBe ident
                    status shouldBe IKKE_DAGPENGERBRUKER
                }
        }

        @Test
        fun `søknad for eksisterende person`() {
            testPerson {
                coEvery { arbeidssøkerConnector.hentSisteArbeidssøkerperiode(any()) } returns emptyList()
                personMediator.behandle(søknadHendelse(ident))

                personRepository
                    .hentPerson(ident)
                    ?.apply {
                        ident shouldBe ident
                        status shouldBe IKKE_DAGPENGERBRUKER
                    }
            }
        }
    }

    @Nested
    inner class VedtakHendelser {
        @Test
        fun `innvilget-vedtak for ny person`() {
            personMediator.behandle(søknadHendelse(ident))
            personMediator.behandle(vedtakHendelse(ident))

            personRepository
                .hentPerson(ident)
                ?.apply {
                    ident shouldBe ident
                    ansvarligSystem shouldBe AnsvarligSystem.DP
                    status shouldBe IKKE_DAGPENGERBRUKER
                }
        }

        @Test
        fun `innvilget-vedtak for arbeidssøker`() {
            arbeidssøker { }

            personMediator.behandle(søknadHendelse(ident))

            val person = personRepository.hentPerson(ident)!!
            every { personObserver.overtattArbeidssøkerbekreftelse(any(), periodeId) } answers {
                person.merkPeriodeSomOvertatt(periodeId)
            }

            personMediator.behandle(vedtakHendelse(ident))

            personRepository
                .hentPerson(ident)
                ?.apply {
                    ident shouldBe ident
                    ansvarligSystem shouldBe AnsvarligSystem.DP
                    status shouldBe DAGPENGERBRUKER
                }

            with(person) {
                status shouldBe DAGPENGERBRUKER
                runBlocking { arbeidssøkerMediator.behandle(PaaVegneAv(periodeId, Bekreftelsesloesning.DAGPENGER, Start())) }
                arbeidssøkerperioder.gjeldende?.overtattBekreftelse shouldBe true
                personObserver skalHaSendtOvertakelseFor this
                this skalHaSendtStartMeldingFor nå
            }
        }

        @Test
        fun `avslag-vedtak for dagpengerbruker`() {
            arbeidssøker(overtattBekreftelse = true) {}
            personMediator.behandle(søknadHendelse(ident))
            personMediator.behandle(vedtakHendelse(ident, utfall = true))

            val person = personRepository.hentPerson(ident)!!
            every { personObserver.frasagtArbeidssøkerbekreftelse(any(), periodeId) } answers {
                person.merkPeriodeSomIkkeOvertatt(periodeId)
            }

            personMediator.behandle(vedtakHendelse(ident, utfall = false))
            personRepository
                .hentPerson(ident)
                ?.apply {
                    ident shouldBe ident
                    ansvarligSystem shouldBe AnsvarligSystem.DP
                    status shouldBe IKKE_DAGPENGERBRUKER
                }

            with(person) {
                status shouldBe IKKE_DAGPENGERBRUKER
                runBlocking { arbeidssøkerMediator.behandle(PaaVegneAv(periodeId, Bekreftelsesloesning.DAGPENGER, Stopp())) }
                arbeidssøkerperioder.gjeldende?.overtattBekreftelse shouldBe false
                personObserver skalHaFrasagtAnsvaretFor this
                this skalHaSendtStoppMeldingFor nå
            }
        }
    }

    @Nested
    inner class Meldegruppeendring {
        @Test
        fun `meldegruppendring for ny person`() {
            personMediator.behandle(annenMeldegruppeHendelse())
            personRepository.hentPerson(ident) shouldBe null

            personMediator.behandle(dagpengerMeldegruppeHendelse())
            personRepository.hentPerson(ident) shouldNotBe null
        }

        @Test
        fun `meldegruppeendring for ny person trigger henting av meldeplikt og arbeidssøkerperiode`() {
            val dagpengerMeldegruppeHendelse = dagpengerMeldegruppeHendelse()
            coEvery { arbeidssøkerConnector.hentSisteArbeidssøkerperiode(dagpengerMeldegruppeHendelse.ident) } returns
                arbeidssøkerperiodeResponse()
            coEvery { meldepliktConnector.hentMeldeplikt(dagpengerMeldegruppeHendelse.ident) } returns true

            personMediator.behandle(dagpengerMeldegruppeHendelse)
            with(personRepository.hentPerson(ident)) {
                this shouldNotBe null
                this?.meldegruppe shouldBe "DAGP"
                this?.meldeplikt shouldBe true
                this?.arbeidssøkerperioder?.gjeldende shouldNotBe null
            }
        }

        @Test
        fun `meldegruppendring for eksisterende person som ikke oppfyller krav`() {
            testPerson {
                personMediator.behandle(dagpengerMeldegruppeHendelse())
                status shouldBe IKKE_DAGPENGERBRUKER
                personObserver skalIkkeHaSendtOvertakelseFor this

                personMediator.behandle(annenMeldegruppeHendelse())
                status shouldBe IKKE_DAGPENGERBRUKER
            }
        }

        @Test
        fun `meldegruppeendring for person ved innvilget vedtak tas ikke hensyn til`() {
            arbeidssøker {}
            personMediator.behandle(søknadHendelse())
            personMediator.behandle(vedtakHendelse(ident))
            personMediator.behandle(dagpengerMeldegruppeHendelse(startDato = nå))
            personMediator.behandle(annenMeldegruppeHendelse(startDato = nå))

            with(personRepository.hentPerson(ident)!!) {
                this.status shouldBe DAGPENGERBRUKER
                this.hendelser.size shouldBe 2
                this.hendelser.first().javaClass shouldBe SøknadHendelse::class.java
                this.hendelser.last().javaClass shouldBe VedtakHendelse::class.java
            }
        }

        @Test
        fun `meldegruppendring for eksisterende person som oppfyller krav`() {
            arbeidssøker {}

            meldepliktMediator.behandle(meldepliktHendelse())
            val person = personRepository.hentPerson(ident)!!
            every { personObserver.overtattArbeidssøkerbekreftelse(any(), periodeId) } answers {
                person.merkPeriodeSomOvertatt(periodeId)
            }
            personMediator.behandle(dagpengerMeldegruppeHendelse(startDato = nå))

            with(person) {
                status shouldBe DAGPENGERBRUKER
                runBlocking { arbeidssøkerMediator.behandle(PaaVegneAv(periodeId, Bekreftelsesloesning.DAGPENGER, Start())) }
                arbeidssøkerperioder.gjeldende?.overtattBekreftelse shouldBe true
                personObserver skalHaSendtOvertakelseFor this
            }

            val person2 = personRepository.hentPerson(ident)!!
            every { personObserver.frasagtArbeidssøkerbekreftelse(any(), periodeId) } answers {
                person2.merkPeriodeSomIkkeOvertatt(periodeId)
            }
            personMediator.behandle(annenMeldegruppeHendelse(startDato = nå.plusDays(1)))
            with(person2) {
                status shouldBe IKKE_DAGPENGERBRUKER
                runBlocking { arbeidssøkerMediator.behandle(PaaVegneAv(periodeId, Bekreftelsesloesning.DAGPENGER, Stopp())) }
                arbeidssøkerperioder.gjeldende?.overtattBekreftelse shouldBe false
                personObserver skalHaFrasagtAnsvaretFor this
            }
        }

        @Test
        fun `meldegruppendring for tidligere periode tas ikke høyde for`() {
            arbeidssøker { }

            meldepliktMediator.behandle(meldepliktHendelse())
            val person = personRepository.hentPerson(ident)!!
            every { personObserver.overtattArbeidssøkerbekreftelse(any(), periodeId) } answers {
                person.merkPeriodeSomOvertatt(periodeId)
            }
            personMediator.behandle(dagpengerMeldegruppeHendelse())
            runBlocking { arbeidssøkerMediator.behandle(PaaVegneAv(periodeId, Bekreftelsesloesning.DAGPENGER, Start())) }
            with(person) {
                status shouldBe DAGPENGERBRUKER
                arbeidssøkerperioder.gjeldende?.overtattBekreftelse shouldBe true
                personObserver skalHaSendtOvertakelseFor this
                hendelser.size shouldBe 2
            }

            personMediator.behandle(annenMeldegruppeHendelse(sluttDato = LocalDateTime.now().minusDays(1)))
            with(personRepository.hentPerson(ident)!!) {
                status shouldBe DAGPENGERBRUKER
                arbeidssøkerperioder.gjeldende?.overtattBekreftelse shouldBe true
                hendelser.size shouldBe 2
            }

            personMediator.behandle(dagpengerMeldegruppeHendelse(sluttDato = LocalDateTime.now().minusDays(1)))
            with(personRepository.hentPerson(ident)!!) {
                status shouldBe DAGPENGERBRUKER
                hendelser.size shouldBe 2
            }
        }

        @Test
        fun `dagpengerMeldegruppeHendelse for person som ikke eksisterer oppretter person og henter meldeplikt`() {
            testPerson {
                coEvery { meldepliktConnector.hentMeldeplikt(ident) } returns true
                personMediator.behandle(dagpengerMeldegruppeHendelse())
                with(personRepository.hentPerson(ident)) {
                    this shouldNotBe null
                    this?.meldegruppe shouldBe "DAGP"
                    this?.meldeplikt shouldBe true
                }

                coVerify(exactly = 1) { meldepliktConnector.hentMeldeplikt(ident) }
            }
        }
    }

    @Nested
    inner class ArbeidssøkerBekreftelse {
        @Test
        fun `overtar arbeidssøker bekreftelse når man blir dagpengerbruker`() {
            arbeidssøker {
                meldepliktMediator.behandle(meldepliktHendelse())
                personMediator.behandle(annenMeldegruppeHendelse())

                statusHistorikk.put(tidligere, IKKE_DAGPENGERBRUKER)
                personRepository.oppdaterPerson(this)

                status shouldBe IKKE_DAGPENGERBRUKER

                personMediator.behandle(dagpengerMeldegruppeHendelse())

                status shouldBe DAGPENGERBRUKER
                verify(exactly = 1) { personObserver.sendOvertakelsesmelding(any()) }
            }
        }

        @Test
        fun `frasier arbeidssøker bekreftelse`() {
            arbeidssøker(overtattBekreftelse = true) {
                meldepliktMediator.behandle(meldepliktHendelse())
                personMediator.behandle(dagpengerMeldegruppeHendelse())

                personRepository.oppdaterPerson(this)

                status shouldBe DAGPENGERBRUKER

                personMediator.behandle(annenMeldegruppeHendelse())

                status shouldBe IKKE_DAGPENGERBRUKER
                verify(exactly = 1) { personObserver.sendFrasigelsesmelding(any()) }
            }
        }
    }

    @Nested
    inner class ArbeidssøkerBeslutning {
        @Test
        fun `lagrer beslutning ved overtakelse av arbeidssøkerbekreftelse`() {
            arbeidssøker {
                meldepliktMediator.behandle(meldepliktHendelse())
                personMediator.behandle(dagpengerMeldegruppeHendelse())
                runBlocking { arbeidssøkerMediator.behandle(PaaVegneAv(periodeId, Bekreftelsesloesning.DAGPENGER, Start())) }

                beslutningRepository.hentBeslutninger(ident) shouldHaveSize 1

                beslutningRepository.hentBeslutning(ident)?.apply {
                    ident shouldBe ident
                    periodeId shouldBe periodeId
                    handling shouldBe Handling.OVERTATT
                    begrunnelse shouldBe "Overtar bekreftelse"
                }
            }
        }

        @Disabled
        @Test
        fun `lagrer beslutning ved frasigelse av arbeidssøkerbekreftelse`() {
            arbeidssøker(overtattBekreftelse = false) {
                meldepliktMediator.behandle(meldepliktHendelse())
                personMediator.behandle(dagpengerMeldegruppeHendelse())
                runBlocking { arbeidssøkerMediator.behandle(PaaVegneAv(periodeId, Bekreftelsesloesning.DAGPENGER, Start())) }

                beslutningRepository.hentBeslutninger(ident) shouldHaveSize 1
                beslutningRepository.hentBeslutning(ident)?.apply {
                    handling shouldBe Handling.OVERTATT
                }

                personMediator.behandle(annenMeldegruppeHendelse())
                runBlocking { arbeidssøkerMediator.behandle(PaaVegneAv(periodeId, Bekreftelsesloesning.DAGPENGER, Stopp())) }
                beslutningRepository.hentBeslutninger(ident) shouldHaveSize 2
            }
        }
    }

    @Nested
    inner class MeldesyklusErPassert {
        @Test
        fun `send frasigelsesmelding når meldesyklus er passert`() {
            arbeidssøker(overtattBekreftelse = true) {
                personMediator.behandle(meldesyklusErPassertHendelse())
                personObserver skalHaFrasagtAnsvaretMedFristBruttFor this
            }
        }
    }

    @Nested
    inner class Nødbrems {
        @Test
        fun `sender stoppmelding og tilbakestiller ansvarlig system ved nødbrems`() {
            dagpengebrukerMedVedtak {
                with(this) {
                    ansvarligSystem shouldBe AnsvarligSystem.DP
                    harRettTilDp shouldBe true
                    status shouldBe DAGPENGERBRUKER
                }

                personMediator.behandle(nødbremsHendelse())
                with(this) {
                    this skalHaSendtStoppMeldingFor nå
                    ansvarligSystem shouldBe AnsvarligSystem.ARENA
                    harRettTilDp shouldBe false
                    status shouldBe DAGPENGERBRUKER
                }
            }
        }
    }

    private fun testPerson(block: Person.() -> Unit) {
        val person = Person(ident = ident)
        personRepository.lagrePerson(person)
        person.apply(block)
    }

    private fun arbeidssøker(
        overtattBekreftelse: Boolean = false,
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
            )
        personRepository.lagrePerson(person)
        person.apply(block)
    }

    private fun dagpengebrukerMedVedtak(block: Person.() -> Unit) {
        Person(
            ident = ident,
            arbeidssøkerperioder =
                mutableListOf(
                    Arbeidssøkerperiode(
                        periodeId,
                        ident,
                        tidligere,
                        null,
                        true,
                    ),
                ),
        ).apply { personRepository.lagrePerson(this) }
        personMediator.behandle(søknadHendelse())
        personMediator.behandle(vedtakHendelse())

        val person = personRepository.hentPerson(ident)!!
        person.apply(block)
    }

    private fun nødbremsHendelse(
        ident: String = this.ident,
        dato: LocalDateTime = nå,
        startDato: LocalDateTime = nå,
        referanseId: String = "321",
    ) = NødbremsHendelse(ident, dato, startDato, referanseId)

    private fun meldesyklusErPassertHendelse(
        ident: String = this.ident,
        dato: LocalDateTime = nå,
        startDato: LocalDateTime = nå,
        referanseId: String = "123",
    ) = MeldesyklusErPassertHendelse(ident, dato, startDato, referanseId)

    private fun søknadHendelse(
        ident: String = this.ident,
        dato: LocalDateTime = nå,
        startDato: LocalDateTime = nå,
        referanseId: String = "123",
    ) = SøknadHendelse(ident, dato, startDato, referanseId)

    private fun vedtakHendelse(
        ident: String = this.ident,
        dato: LocalDateTime = nå,
        startDato: LocalDateTime = nå,
        referanseId: String = "456",
        utfall: Boolean = true,
    ) = VedtakHendelse(ident, dato, startDato, referanseId, startDato.plusDays(10), utfall)

    private fun dagpengerMeldegruppeHendelse(
        dato: LocalDateTime = nå,
        startDato: LocalDateTime = nå,
        sluttDato: LocalDateTime? = null,
        referanseId: String = "123",
    ) = DagpengerMeldegruppeHendelse(ident, dato, referanseId, startDato = startDato, sluttDato = sluttDato, "DAGP", harMeldtSeg = true)

    private fun annenMeldegruppeHendelse(
        dato: LocalDateTime = nå,
        startDato: LocalDateTime = nå,
        sluttDato: LocalDateTime? = null,
        referanseId: String = "123",
    ) = AnnenMeldegruppeHendelse(ident, dato, referanseId, startDato = startDato, sluttDato = sluttDato, "ARBS", harMeldtSeg = true)

    private fun meldepliktHendelse(
        dato: LocalDateTime = nå,
        sluttDato: LocalDateTime? = null,
        referanseId: String = "123",
        status: Boolean = true,
    ) = MeldepliktHendelse(
        ident = ident,
        dato = dato,
        startDato = dato,
        sluttDato = sluttDato,
        statusMeldeplikt = status,
        referanseId = referanseId,
        harMeldtSeg = true,
    )

    private fun arbeidssøkerperiodeResponse(
        periodeId: UUID = UUID.randomUUID(),
        startet: OffsetDateTime = OffsetDateTime.now(),
    ) = listOf(
        ArbeidssøkerperiodeResponse(
            periodeId = periodeId,
            startet =
                MetadataResponse(
                    tidspunkt = startet,
                    utfoertAv =
                        BrukerResponse(
                            type = "type",
                            id = "ID",
                        ),
                    kilde = "kilde",
                    aarsak = "Årsak",
                    tidspunktFraKilde = null,
                ),
            avsluttet = null,
        ),
    )
}

infix fun PersonObserver.skalHaSendtOvertakelseFor(person: Person) {
    verify(exactly = 1) { sendOvertakelsesmelding(person) }
}

infix fun PersonObserver.skalIkkeHaSendtOvertakelseFor(person: Person) {
    verify(exactly = 0) { sendOvertakelsesmelding(person) }
}

infix fun PersonObserver.skalHaFrasagtAnsvaretFor(person: Person) {
    verify(exactly = 1) { sendFrasigelsesmelding(person) }
}

infix fun PersonObserver.skalHaFrasagtAnsvaretMedFristBruttFor(person: Person) {
    verify(exactly = 1) { sendFrasigelsesmelding(person, fristBrutt = true) }
}

infix fun Person.skalHaSendtStartMeldingFor(startDato: LocalDateTime) {
    verify(exactly = 1) { sendStartMeldingTilMeldekortregister(startDato, any()) }
}

infix fun Person.skalHaSendtStoppMeldingFor(stoppDato: LocalDateTime) {
    verify(exactly = 1) { sendStoppMeldingTilMeldekortregister(stoppDato) }
}

class BeslutningObserver(
    private val beslutningRepository: ArbeidssøkerBeslutningRepository,
) : PersonObserver {
    override fun overtattArbeidssøkerbekreftelse(
        person: Person,
        periodeId: UUID,
    ) {
        val beslutning =
            ArbeidssøkerBeslutning(
                person.ident,
                periodeId,
                Handling.OVERTATT,
                begrunnelse =
                    "Oppfyller krav: arbedissøker, " +
                        "meldeplikt=${person.meldeplikt} " +
                        "og gruppe=${person.meldegruppe}",
            )

        beslutningRepository.lagreBeslutning(beslutning)
    }

    override fun frasagtArbeidssøkerbekreftelse(
        person: Person,
        periodeId: UUID,
    ) {
        val beslutning =
            ArbeidssøkerBeslutning(
                person.ident,
                periodeId,
                Handling.FRASAGT,
                begrunnelse = "Ikke opppfyller krav",
            )

        beslutningRepository.lagreBeslutning(beslutning)
    }
}

class ArbeidssøkerBeslutningRepositoryFaker : ArbeidssøkerBeslutningRepository {
    private val beslutninger = mutableListOf<ArbeidssøkerBeslutning>()

    override fun hentBeslutning(id: String) = beslutninger.lastOrNull { it.periodeId.toString() == id }

    override fun lagreBeslutning(beslutning: ArbeidssøkerBeslutning) {
        beslutninger.add(beslutning)
    }

    override fun hentBeslutninger(ident: String) = beslutninger.filter { it.ident == ident }
}
