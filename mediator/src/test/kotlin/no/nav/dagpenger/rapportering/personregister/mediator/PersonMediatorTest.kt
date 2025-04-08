package no.nav.dagpenger.rapportering.personregister.mediator

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import no.nav.dagpenger.rapportering.personregister.mediator.connector.ArbeidssøkerConnector
import no.nav.dagpenger.rapportering.personregister.mediator.db.ArbeidssøkerBeslutningRepository
import no.nav.dagpenger.rapportering.personregister.mediator.db.InMemoryPersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.service.ArbeidssøkerService
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.ArbeidssøkerBeslutning
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.Handling
import no.nav.dagpenger.rapportering.personregister.mediator.utils.MetrikkerTestUtil.actionTimer
import no.nav.dagpenger.rapportering.personregister.mediator.utils.kafka.MockKafkaProducer
import no.nav.dagpenger.rapportering.personregister.modell.AnnenMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.Arbeidssøkerperiode
import no.nav.dagpenger.rapportering.personregister.modell.DagpengerMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.MeldepliktHendelse
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.PersonObserver
import no.nav.dagpenger.rapportering.personregister.modell.Status.DAGPENGERBRUKER
import no.nav.dagpenger.rapportering.personregister.modell.Status.IKKE_DAGPENGERBRUKER
import no.nav.dagpenger.rapportering.personregister.modell.SøknadHendelse
import no.nav.dagpenger.rapportering.personregister.modell.gjeldende
import no.nav.paw.bekreftelse.paavegneav.v1.PaaVegneAv
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class PersonMediatorTest {
    private lateinit var rapidsConnection: TestRapid
    private lateinit var personRepository: PersonRepository
    private lateinit var arbeidssøkerConnector: ArbeidssøkerConnector
    private lateinit var overtaBekreftelseKafkaProdusent: MockKafkaProducer<PaaVegneAv>
    private lateinit var personMediator: PersonMediator
    private lateinit var arbeidssøkerService: ArbeidssøkerService
    private lateinit var arbeidssøkerMediator: ArbeidssøkerMediator

    private lateinit var beslutningRepository: ArbeidssøkerBeslutningRepository
    private val personObserver = mockk<PersonObserver>(relaxed = true)
    private lateinit var beslutningObserver: BeslutningObserver

    @BeforeEach
    fun setup() {
        rapidsConnection = TestRapid()
        personRepository = InMemoryPersonRepository()
        arbeidssøkerConnector = mockk<ArbeidssøkerConnector>(relaxed = true)
        overtaBekreftelseKafkaProdusent = MockKafkaProducer()
        arbeidssøkerService = ArbeidssøkerService(arbeidssøkerConnector)
        arbeidssøkerMediator = ArbeidssøkerMediator(arbeidssøkerService, personRepository, listOf(personObserver), actionTimer)
        beslutningRepository = ArbeidssøkerBeslutningRepositoryFaker()
        beslutningObserver = BeslutningObserver(beslutningRepository)
        personMediator = PersonMediator(personRepository, arbeidssøkerMediator, listOf(personObserver, beslutningObserver), actionTimer)
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
    inner class Meldegruppeendring {
        @Test
        fun `meldegruppendring for ny person`() {
            personMediator.behandle(dagpengerMeldegruppeHendelse())
            personRepository.hentPerson(ident) shouldBe null

            personMediator.behandle(annenMeldegruppeHendelse())
            personRepository.hentPerson(ident) shouldBe null
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
        fun `meldegruppendring for eksisterende person som oppfyller krav`() {
            arbeidssøker {
                personMediator.behandle(meldepliktHendelse())
                personMediator.behandle(dagpengerMeldegruppeHendelse())
                status shouldBe DAGPENGERBRUKER
                arbeidssøkerperioder.gjeldende?.overtattBekreftelse shouldBe true
                personObserver skalHaSendtOvertakelseFor this

                personMediator.behandle(annenMeldegruppeHendelse())
                status shouldBe IKKE_DAGPENGERBRUKER
                arbeidssøkerperioder.gjeldende?.overtattBekreftelse shouldBe false
                personObserver skalHaFrasagtAnsvaretFor this
            }
        }

        @Test
        fun `meldegruppendring for tidligere periode tas ikke høyde for`() {
            arbeidssøker {
                personMediator.behandle(meldepliktHendelse())
                personMediator.behandle(dagpengerMeldegruppeHendelse())
                status shouldBe DAGPENGERBRUKER
                arbeidssøkerperioder.gjeldende?.overtattBekreftelse shouldBe true
                personObserver skalHaSendtOvertakelseFor this
                hendelser.size shouldBe 2

                personMediator.behandle(annenMeldegruppeHendelse(sluttDato = LocalDateTime.now().minusDays(1)))
                status shouldBe DAGPENGERBRUKER
                arbeidssøkerperioder.gjeldende?.overtattBekreftelse shouldBe true
                hendelser.size shouldBe 2

                personMediator.behandle(dagpengerMeldegruppeHendelse(sluttDato = LocalDateTime.now().minusDays(1)))
                status shouldBe DAGPENGERBRUKER
                hendelser.size shouldBe 2
            }
        }
    }

    @Nested
    inner class Meldingsplikt {
        @Test
        fun `meldepliktendring for ny person`() {
            personMediator.behandle(meldepliktHendelse())
            personRepository.hentPerson(ident) shouldBe null
        }

        @Test
        fun `meldepliktendring for eksisterende person som ikke oppfyller krav`() {
            arbeidssøker {
                personMediator.behandle(meldepliktHendelse(status = false))
                status shouldBe IKKE_DAGPENGERBRUKER
                personObserver skalIkkeHaSendtOvertakelseFor this
            }
        }

        @Test
        fun `meldepliktendring for eksisterende person som oppfyller krav`() {
            arbeidssøker {
                personMediator.behandle(dagpengerMeldegruppeHendelse())
                personMediator.behandle(meldepliktHendelse(status = true))
                status shouldBe DAGPENGERBRUKER
                personObserver skalHaSendtOvertakelseFor this
            }
        }

        @Test
        fun `oppdaterer ikke statushistorikk dersom bruker får samme status`() {
            arbeidssøker {
                personMediator.behandle(meldepliktHendelse())
                statusHistorikk.getAll() shouldHaveSize 1

                personMediator.behandle(dagpengerMeldegruppeHendelse())
                statusHistorikk.getAll() shouldHaveSize 2

                personMediator.behandle(dagpengerMeldegruppeHendelse())
                status shouldBe DAGPENGERBRUKER
                statusHistorikk.getAll() shouldHaveSize 2
            }
        }

        @Test
        fun `meldeplikthendelse for tidligere periode tas ikke høyde for`() {
            arbeidssøker {
                personMediator.behandle(meldepliktHendelse())
                personMediator.behandle(dagpengerMeldegruppeHendelse())
                status shouldBe DAGPENGERBRUKER
                arbeidssøkerperioder.gjeldende?.overtattBekreftelse shouldBe true
                personObserver skalHaSendtOvertakelseFor this
                hendelser.size shouldBe 2

                personMediator.behandle(meldepliktHendelse(sluttDato = LocalDateTime.now().minusDays(1), status = false))
                status shouldBe DAGPENGERBRUKER
                arbeidssøkerperioder.gjeldende?.overtattBekreftelse shouldBe true
                hendelser.size shouldBe 2
            }
        }
    }

    @Nested
    inner class ArbeidssøkerBekreftelse {
        @Test
        fun `overtar arbeidssøker bekreftelse når man blir dagpengerbruker`() {
            arbeidssøker {
                personMediator.behandle(meldepliktHendelse())
                personMediator.behandle(annenMeldegruppeHendelse())

                statusHistorikk.put(tidligere, IKKE_DAGPENGERBRUKER)
                personRepository.oppdaterPerson(this)

                status shouldBe IKKE_DAGPENGERBRUKER

                personMediator.behandle(dagpengerMeldegruppeHendelse())

                status shouldBe DAGPENGERBRUKER
                personObserver skalHaSendtOvertakelseFor this
            }
        }

        @Test
        fun `sender ikke overtakelsesmelding dersom vi allerede har overtatt arbeidssøker bekreftelse`() {
            arbeidssøker(overtattBekreftelse = true) {
                personMediator.behandle(meldepliktHendelse())
                personMediator.behandle(annenMeldegruppeHendelse())
                personRepository.oppdaterPerson(this)

                status shouldBe IKKE_DAGPENGERBRUKER

                personMediator.behandle(dagpengerMeldegruppeHendelse())

                status shouldBe DAGPENGERBRUKER
                personObserver skalIkkeHaSendtOvertakelseFor this
            }
        }

        @Test
        fun `frasier arbeidssøker bekreftelse`() {
            arbeidssøker(overtattBekreftelse = true) {
                personMediator.behandle(meldepliktHendelse())
                personMediator.behandle(dagpengerMeldegruppeHendelse())

                personRepository.oppdaterPerson(this)

                status shouldBe DAGPENGERBRUKER

                personMediator.behandle(annenMeldegruppeHendelse())

                status shouldBe IKKE_DAGPENGERBRUKER
                personObserver skalHaFrasagtAnsvaretFor this
            }
        }
    }

    @Nested
    inner class ArbeidssøkerBeslutning {
        @Test
        fun `lagrer beslutning ved overtakelse av arbeidssøkerbekreftelse`() {
            arbeidssøker {
                personMediator.behandle(meldepliktHendelse())
                personMediator.behandle(dagpengerMeldegruppeHendelse())

                beslutningRepository.hentBeslutninger(ident) shouldHaveSize 1
                beslutningRepository.hentBeslutning(ident)?.apply {
                    ident shouldBe ident
                    periodeId shouldBe periodeId
                    handling shouldBe Handling.OVERTATT
                    begrunnelse shouldBe "Overtar bekreftelse"
                }
            }
        }

        @Test
        fun `lagrer beslutning ved frasigelse av arbeidssøkerbekreftelse`() {
            arbeidssøker {
                personMediator.behandle(meldepliktHendelse())
                personMediator.behandle(dagpengerMeldegruppeHendelse())

                beslutningRepository.hentBeslutninger(ident) shouldHaveSize 1
                beslutningRepository.hentBeslutning(ident)?.apply {
                    handling shouldBe Handling.OVERTATT
                }

                personMediator.behandle(annenMeldegruppeHendelse())
                beslutningRepository.hentBeslutninger(ident) shouldHaveSize 2
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

    private fun søknadHendelse(
        ident: String = this.ident,
        dato: LocalDateTime = nå,
        referanseId: String = "123",
    ) = SøknadHendelse(ident, dato, referanseId)

    private fun dagpengerMeldegruppeHendelse(
        dato: LocalDateTime = nå,
        sluttDato: LocalDateTime? = null,
        referanseId: String = "123",
    ) = DagpengerMeldegruppeHendelse(ident, dato, referanseId, startDato = dato, sluttDato = sluttDato, "DAGP", harMeldtSeg = true)

    private fun annenMeldegruppeHendelse(
        dato: LocalDateTime = nå,
        sluttDato: LocalDateTime? = null,
        referanseId: String = "123",
    ) = AnnenMeldegruppeHendelse(ident, dato, referanseId, startDato = dato, sluttDato = sluttDato, "ARBS", harMeldtSeg = true)

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
}

infix fun PersonObserver.skalHaSendtOvertakelseFor(person: Person) {
    verify(exactly = 1) { overtaArbeidssøkerBekreftelse(person) }
}

infix fun PersonObserver.skalIkkeHaSendtOvertakelseFor(person: Person) {
    verify(exactly = 0) { overtaArbeidssøkerBekreftelse(person) }
}

infix fun PersonObserver.skalHaFrasagtAnsvaretFor(person: Person) {
    verify(exactly = 1) { frasiArbeidssøkerBekreftelse(person) }
}

class BeslutningObserver(
    private val beslutningRepository: ArbeidssøkerBeslutningRepository,
) : PersonObserver {
    override fun overtaArbeidssøkerBekreftelse(person: Person) {
        person.arbeidssøkerperioder.gjeldende
            ?.let { periode ->
                val beslutning =
                    ArbeidssøkerBeslutning(
                        person.ident,
                        periode.periodeId,
                        Handling.OVERTATT,
                        begrunnelse =
                            "Oppfyller krav: arbedissøker, " +
                                "meldeplikt=${person.meldeplikt} " +
                                "og gruppe=${person.meldegruppe}",
                    )

                beslutningRepository.lagreBeslutning(beslutning)
            }
    }

    override fun frasiArbeidssøkerBekreftelse(
        person: Person,
        fristBrutt: Boolean,
    ) {
        val periodeId = person.arbeidssøkerperioder.gjeldende?.periodeId
        val beslutning =
            ArbeidssøkerBeslutning(
                person.ident,
                periodeId!!,
                Handling.FRASAGT,
                begrunnelse = "Ikke opppfyller krav",
            )

        beslutningRepository.lagreBeslutning(beslutning)
    }
}

class ArbeidssøkerBeslutningRepositoryFaker : ArbeidssøkerBeslutningRepository {
    private val beslutninger = mutableListOf<ArbeidssøkerBeslutning>()

    override fun hentBeslutning(periodeId: String) = beslutninger.lastOrNull { it.periodeId.toString() == periodeId }

    override fun lagreBeslutning(beslutning: ArbeidssøkerBeslutning) {
        beslutninger.add(beslutning)
    }

    override fun hentBeslutninger(ident: String) = beslutninger.filter { it.ident == ident }
}
