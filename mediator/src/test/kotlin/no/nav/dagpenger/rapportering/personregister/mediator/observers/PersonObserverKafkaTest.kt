package no.nav.dagpenger.rapportering.personregister.mediator.observers

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import no.nav.dagpenger.rapportering.personregister.mediator.connector.ArbeidssøkerConnector
import no.nav.dagpenger.rapportering.personregister.mediator.connector.RecordKeyResponse
import no.nav.dagpenger.rapportering.personregister.mediator.utils.kafka.MockKafkaProducer
import no.nav.dagpenger.rapportering.personregister.modell.Arbeidssøkerperiode
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.paw.bekreftelse.paavegneav.v1.PaaVegneAv
import no.nav.paw.bekreftelse.paavegneav.v1.vo.Bekreftelsesloesning
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class PersonObserverKafkaTest {
    private lateinit var producer: MockKafkaProducer<PaaVegneAv>
    private lateinit var arbeidssøkerConnector: ArbeidssøkerConnector
    private lateinit var personObserverKafka: PersonObserverKafka
    private val bekreftelsePåVegnAvTopic = "bekreftelsePåVegnAvTopic"

    @BeforeEach
    fun setup() {
        producer = MockKafkaProducer()
        arbeidssøkerConnector = mockk(relaxed = true)
        personObserverKafka = PersonObserverKafka(producer, arbeidssøkerConnector, bekreftelsePåVegnAvTopic)
    }

    @Test
    fun `kan ikke overta arbeidssøkerbekreftelse når person ikke har arbeidssøkerperiode`() {
        val person = Person("12345678910")

        personObserverKafka.frasiArbeidssøkerBekreftelse(person, fristBrutt = false)

        coVerify(exactly = 0) { arbeidssøkerConnector.hentRecordKey(person.ident) }
        producer.meldinger shouldBe emptyList()
    }

    @Test
    fun `kan overta arbeidssøkerbekreftelse`() {
        val person = lagPersonMedArbeidssøkerperiode()
        coEvery { arbeidssøkerConnector.hentRecordKey(person.ident) } returns RecordKeyResponse(1)

        personObserverKafka.overtaArbeidssøkerBekreftelse(person)

        verifiserKafkaMelding(person)
    }

    @Test
    fun `kan frasi ansvaret for arbeidssøkerbekreftelse`() {
        val person = lagPersonMedArbeidssøkerperiode()
        coEvery { arbeidssøkerConnector.hentRecordKey(person.ident) } returns RecordKeyResponse(1)

        personObserverKafka.frasiArbeidssøkerBekreftelse(person, fristBrutt = false)

        verifiserKafkaMelding(person)
    }

    private fun lagPersonMedArbeidssøkerperiode(): Person {
        val ident = "12345678910"
        val periodeId = UUID.randomUUID()
        return Person(
            ident = ident,
            arbeidssøkerperioder =
                mutableListOf(
                    Arbeidssøkerperiode(
                        periodeId = periodeId,
                        ident = ident,
                        startet = LocalDateTime.now(),
                        avsluttet = null,
                        overtattBekreftelse = false,
                    ),
                ),
        )
    }

    private fun verifiserKafkaMelding(person: Person) {
        coVerify(exactly = 1) { arbeidssøkerConnector.hentRecordKey(person.ident) }
        with(producer.meldinger) {
            size shouldBe 1
            with(first()) {
                topic() shouldBe bekreftelsePåVegnAvTopic
                key() shouldBe 1
                value().periodeId shouldBe person.arbeidssøkerperioder.first().periodeId
                value().bekreftelsesloesning shouldBe Bekreftelsesloesning.DAGPENGER
            }
        }
    }
}
