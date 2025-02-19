package no.nav.dagpenger.rapportering.personregister.mediator.observers

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import no.nav.dagpenger.rapportering.personregister.mediator.connector.ArbeidssøkerConnector
import no.nav.dagpenger.rapportering.personregister.mediator.connector.RecordKeyResponse
import no.nav.dagpenger.rapportering.personregister.mediator.db.PostrgesArbeidssøkerRepository
import no.nav.dagpenger.rapportering.personregister.mediator.utils.kafka.MockKafkaProducer
import no.nav.dagpenger.rapportering.personregister.modell.Arbeidssøkerperiode
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.paw.bekreftelse.paavegneav.v1.PaaVegneAv
import no.nav.paw.bekreftelse.paavegneav.v1.vo.Bekreftelsesloesning
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class PersonObserverKafkaTest {
    private var producer = MockKafkaProducer<PaaVegneAv>()
    private val arbeidssøkerConnector = mockk<ArbeidssøkerConnector>()
    private val arbeidssøkerRepository = mockk<PostrgesArbeidssøkerRepository>()
    private val overtaBekreftelseTopic = "overtaBekreftelseTopic"
    private val frasiBekreftelseTopic = "frasiBekreftelseTopic"

    private val personObserverKafka =
        PersonObserverKafka(
            producer,
            arbeidssøkerConnector,
            arbeidssøkerRepository,
            overtaBekreftelseTopic,
        )

    @Test
    fun `når person ikke har arbeissøkerperioder`() {
        every { arbeidssøkerRepository.hentArbeidssøkerperioder(any()) } returns emptyList()

        val person = Person("12345678910")

        personObserverKafka.frasiArbeidssøkerBekreftelse(person)

        verify(exactly = 1) { arbeidssøkerRepository.hentArbeidssøkerperioder(person.ident) }
        coVerify(exactly = 0) { arbeidssøkerConnector.hentRecordKey(person.ident) }
        producer.meldinger.size shouldBe 0
    }

    @Test
    fun `når person har aktiv arbeidssøkerperiode`() {
        val periodeId = UUID.randomUUID()
        val ident = "12345678910"
        val person = Person(ident)
        val arbeidssøkerperioder =
            listOf(
                Arbeidssøkerperiode(periodeId, ident, LocalDateTime.now(), null, true),
            )

        every { arbeidssøkerRepository.hentArbeidssøkerperioder(any()) } returns arbeidssøkerperioder
        every { arbeidssøkerRepository.oppdaterOvertagelse(periodeId, false) } just runs
        coEvery { arbeidssøkerConnector.hentRecordKey(ident) } returns RecordKeyResponse(1)

        personObserverKafka.frasiArbeidssøkerBekreftelse(person)

        verify(exactly = 1) { arbeidssøkerRepository.hentArbeidssøkerperioder(person.ident) }
        coVerify(exactly = 1) { arbeidssøkerConnector.hentRecordKey(person.ident) }
        with(producer.meldinger) {
            size shouldBe 1
            with(first()) {
                topic() shouldBe frasiBekreftelseTopic
                key() shouldBe 1
                value().periodeId shouldBe periodeId
                value().bekreftelsesloesning shouldBe Bekreftelsesloesning.DAGPENGER
            }
        }
    }
}
