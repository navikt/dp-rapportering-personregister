package no.nav.dagpenger.rapportering.personregister.mediator

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import no.nav.dagpenger.rapportering.personregister.mediator.connector.ArbeidssøkerConnector
import no.nav.dagpenger.rapportering.personregister.mediator.connector.RecordKeyResponse
import no.nav.dagpenger.rapportering.personregister.mediator.db.ArbeidssøkerRepository
import no.nav.dagpenger.rapportering.personregister.mediator.db.ArbeidssøkerRepositoryFaker
import no.nav.dagpenger.rapportering.personregister.mediator.db.InMemoryPersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.service.ArbeidssøkerService
import no.nav.dagpenger.rapportering.personregister.mediator.utils.arbeidssøkerResponse
import no.nav.dagpenger.rapportering.personregister.mediator.utils.kafka.MockKafkaProducer
import no.nav.dagpenger.rapportering.personregister.modell.Arbeidssøkerperiode
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.paw.bekreftelse.paavegneav.v1.PaaVegneAv
import no.nav.paw.bekreftelse.paavegneav.v1.vo.Bekreftelsesloesning.DAGPENGER
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class ArbeidssøkerMediatorTest {
    private lateinit var personRepository: PersonRepository
    private lateinit var arbeidssøkerRepository: ArbeidssøkerRepository
    private lateinit var arbeidssøkerConnector: ArbeidssøkerConnector
    private lateinit var bekreftelsePåVegneAvKafkaProdusent: MockKafkaProducer<PaaVegneAv>
    private lateinit var arbeidssøkerService: ArbeidssøkerService
    private lateinit var arbeidssøkerMediator: ArbeidssøkerMediator
    private val bekreftelsePåVegneAvTopic = "paa_vegne_av"

    @BeforeEach
    fun setup() {
        personRepository = InMemoryPersonRepository()
        arbeidssøkerRepository = ArbeidssøkerRepositoryFaker()
        arbeidssøkerConnector = mockk<ArbeidssøkerConnector>()
        bekreftelsePåVegneAvKafkaProdusent = MockKafkaProducer()
        arbeidssøkerService =
            ArbeidssøkerService(
                personRepository,
                arbeidssøkerRepository,
                arbeidssøkerConnector,
            )
        arbeidssøkerMediator = ArbeidssøkerMediator(arbeidssøkerService, personRepository)
    }

    val person = Person("12345678910")

    private fun arbeidssøkerperiode(
        periodeId: UUID = UUID.randomUUID(),
        ident: String = person.ident,
        startet: LocalDateTime = LocalDateTime.now(),
        avsluttet: LocalDateTime? = null,
        overtattBekreftelse: Boolean? = null,
    ) = Arbeidssøkerperiode(
        periodeId = periodeId,
        ident = ident,
        startet = startet,
        avsluttet = avsluttet,
        overtattBekreftelse = overtattBekreftelse,
    )

    @Test
    fun `kan behandle ny person med arbeidssøkerperiode`() {
        val periodeId = UUID.randomUUID()
        val recordKey = 1234L
        val arbeidssøkerperiodeResponse = arbeidssøkerResponse(periodeId)
        coEvery { arbeidssøkerConnector.hentSisteArbeidssøkerperiode(person.ident) } returns listOf(arbeidssøkerperiodeResponse)
        coEvery { arbeidssøkerConnector.hentRecordKey(person.ident) } returns RecordKeyResponse(recordKey)
        personRepository.hentPerson(person.ident) shouldBe null
        personRepository.lagrePerson(person)

        arbeidssøkerMediator.behandle(person.ident)

        /*with(bekreftelsePåVegneAvKafkaProdusent.meldinger) {
            size shouldBe 1
            with(first()) {
                topic() shouldBe bekreftelsePåVegneAvTopic
                key() shouldBe recordKey
                value().periodeId shouldBe periodeId
                value().bekreftelsesloesning shouldBe DAGPENGER
            }
        }*/

        with(arbeidssøkerRepository.hentArbeidssøkerperioder(person.ident)) {
            size shouldBe 1
            with(first()) {
                ident shouldBe person.ident
                startet shouldBe arbeidssøkerperiodeResponse.startet.tidspunkt
                avsluttet shouldBe arbeidssøkerperiodeResponse.avsluttet?.tidspunkt
                overtattBekreftelse shouldBe true
            }
        }
    }

    @Test
    fun `kan behandle ny arbeidssøkerperiode`() {
        val recordKey = 1234L
        coEvery { arbeidssøkerConnector.hentRecordKey(person.ident) } returns RecordKeyResponse(recordKey)
        personRepository.hentPerson(person.ident) shouldBe null
        personRepository.lagrePerson(person)

        val periode = arbeidssøkerperiode()
        arbeidssøkerMediator.behandle(periode)

        with(arbeidssøkerRepository.hentArbeidssøkerperioder(person.ident)) {
            size shouldBe 1
            with(first()) {
                ident shouldBe person.ident
                startet shouldBe periode.startet
                avsluttet shouldBe periode.avsluttet
                overtattBekreftelse shouldBe true
            }
        }

        with(bekreftelsePåVegneAvKafkaProdusent.meldinger) {
            size shouldBe 1
            with(first()) {
                topic() shouldBe bekreftelsePåVegneAvTopic
                key() shouldBe recordKey
                value().periodeId shouldBe periode.periodeId
                value().bekreftelsesloesning shouldBe DAGPENGER
            }
        }
    }

    @Test
    fun `kan behandle eksisterende arbeidssøkerperiode`() {
        personRepository.lagrePerson(person)

        val periode = arbeidssøkerperiode()
        arbeidssøkerRepository.lagreArbeidssøkerperiode(periode)

        arbeidssøkerRepository.hentArbeidssøkerperioder(person.ident).size shouldBe 1

        val avsluttet = LocalDateTime.now()
        arbeidssøkerMediator.behandle(periode.copy(avsluttet = LocalDateTime.now()))

        with(arbeidssøkerRepository.hentArbeidssøkerperioder(person.ident)) {
            size shouldBe 1
            with(first()) {
                ident shouldBe person.ident
                startet shouldBe periode.startet
                avsluttet shouldBe avsluttet
                overtattBekreftelse shouldBe false
            }
        }

        bekreftelsePåVegneAvKafkaProdusent.meldinger.size shouldBe 0
    }

    @Test
    fun `utfører ingen operasjoner hvis personen perioden omhandler ikke finnes`() {
        val periode = arbeidssøkerperiode()
        arbeidssøkerMediator.behandle(periode)

        arbeidssøkerRepository.hentArbeidssøkerperioder(person.ident).size shouldBe 0

        bekreftelsePåVegneAvKafkaProdusent.meldinger.size shouldBe 0
    }
}
