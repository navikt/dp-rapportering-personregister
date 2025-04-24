package no.nav.dagpenger.rapportering.personregister.mediator

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import no.nav.dagpenger.rapportering.personregister.mediator.connector.ArbeidssøkerConnector
import no.nav.dagpenger.rapportering.personregister.mediator.connector.MeldepliktConnector
import no.nav.dagpenger.rapportering.personregister.mediator.db.ArbeidssøkerBeslutningRepository
import no.nav.dagpenger.rapportering.personregister.mediator.db.InMemoryPersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.service.ArbeidssøkerService
import no.nav.dagpenger.rapportering.personregister.mediator.utils.MetrikkerTestUtil.actionTimer
import no.nav.dagpenger.rapportering.personregister.mediator.utils.kafka.MockKafkaProducer
import no.nav.dagpenger.rapportering.personregister.modell.Arbeidssøkerperiode
import no.nav.dagpenger.rapportering.personregister.modell.DagpengerMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.MeldepliktHendelse
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.PersonObserver
import no.nav.dagpenger.rapportering.personregister.modell.Status.DAGPENGERBRUKER
import no.nav.dagpenger.rapportering.personregister.modell.Status.IKKE_DAGPENGERBRUKER
import no.nav.dagpenger.rapportering.personregister.modell.gjeldende
import no.nav.paw.bekreftelse.paavegneav.v1.PaaVegneAv
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class MeldepliktMediatorTest {
    private lateinit var rapidsConnection: TestRapid
    private lateinit var personRepository: PersonRepository
    private lateinit var arbeidssøkerConnector: ArbeidssøkerConnector
    private lateinit var overtaBekreftelseKafkaProdusent: MockKafkaProducer<PaaVegneAv>
    private lateinit var personMediator: PersonMediator
    private lateinit var arbeidssøkerService: ArbeidssøkerService
    private lateinit var arbeidssøkerMediator: ArbeidssøkerMediator
    private lateinit var meldepliktConnector: MeldepliktConnector
    private lateinit var meldepliktMediator: MeldepliktMediator

    private val personObserver = mockk<PersonObserver>(relaxed = true)
    private lateinit var beslutningRepository: ArbeidssøkerBeslutningRepository
    private lateinit var beslutningObserver: BeslutningObserver

    @BeforeEach
    fun setup() {
        rapidsConnection = TestRapid()
        personRepository = InMemoryPersonRepository()
        arbeidssøkerConnector = mockk<ArbeidssøkerConnector>(relaxed = true)
        overtaBekreftelseKafkaProdusent = MockKafkaProducer()
        arbeidssøkerService = ArbeidssøkerService(arbeidssøkerConnector)
        arbeidssøkerMediator = ArbeidssøkerMediator(arbeidssøkerService, personRepository, listOf(personObserver), actionTimer)
        meldepliktConnector = mockk<MeldepliktConnector>(relaxed = true)
        beslutningRepository = ArbeidssøkerBeslutningRepositoryFaker()
        beslutningObserver = BeslutningObserver(beslutningRepository)

        meldepliktMediator =
            MeldepliktMediator(
                personRepository,
                listOf(personObserver, beslutningObserver),
                meldepliktConnector,
                actionTimer,
            )
        personMediator =
            PersonMediator(
                personRepository,
                arbeidssøkerMediator,
                listOf(personObserver, beslutningObserver),
                meldepliktMediator,
                actionTimer,
            )
    }

    private val ident = "12345678910"
    private val nå = LocalDateTime.now()
    private val tidligere = nå.minusDays(1)
    private val periodeId = UUID.randomUUID()

    @Test
    fun `meldepliktendring for ny person`() {
        meldepliktMediator.behandle(meldepliktHendelse())
        personRepository.hentPerson(ident) shouldBe null
    }

    @Test
    fun `meldepliktendring for eksisterende person som ikke oppfyller krav`() {
        arbeidssøker {
            meldepliktMediator.behandle(meldepliktHendelse(status = false))
            status shouldBe IKKE_DAGPENGERBRUKER
            personObserver skalIkkeHaSendtOvertakelseFor this
        }
    }

    @Test
    fun `meldepliktendring for eksisterende person som oppfyller krav`() {
        arbeidssøker {
            personMediator.behandle(dagpengerMeldegruppeHendelse())
            meldepliktMediator.behandle(meldepliktHendelse(status = true))
            status shouldBe DAGPENGERBRUKER
            personObserver skalHaSendtOvertakelseFor this
        }
    }

    @Test
    fun `oppdaterer ikke statushistorikk dersom bruker får samme status`() {
        arbeidssøker {
            meldepliktMediator.behandle(meldepliktHendelse())
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
            meldepliktMediator.behandle(meldepliktHendelse())
            personMediator.behandle(dagpengerMeldegruppeHendelse())
            status shouldBe DAGPENGERBRUKER
            arbeidssøkerperioder.gjeldende?.overtattBekreftelse shouldBe true
            personObserver skalHaSendtOvertakelseFor this
            hendelser.size shouldBe 2

            meldepliktMediator.behandle(meldepliktHendelse(sluttDato = LocalDateTime.now().minusDays(1), status = false))
            status shouldBe DAGPENGERBRUKER
            arbeidssøkerperioder.gjeldende?.overtattBekreftelse shouldBe true
            hendelser.size shouldBe 2
        }
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

    private fun dagpengerMeldegruppeHendelse(
        dato: LocalDateTime = nå,
        sluttDato: LocalDateTime? = null,
        referanseId: String = "123",
    ) = DagpengerMeldegruppeHendelse(ident, dato, referanseId, startDato = dato, sluttDato = sluttDato, "DAGP", harMeldtSeg = true)

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
