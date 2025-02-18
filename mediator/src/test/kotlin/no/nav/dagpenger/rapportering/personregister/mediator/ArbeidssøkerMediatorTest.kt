package no.nav.dagpenger.rapportering.personregister.mediator

import io.kotest.matchers.shouldBe
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import no.nav.dagpenger.rapportering.personregister.kafka.plugin.KafkaConsumerPlugin
import no.nav.dagpenger.rapportering.personregister.kafka.plugin.KafkaProducerPlugin
import no.nav.dagpenger.rapportering.personregister.mediator.api.ApiTestSetup
import no.nav.dagpenger.rapportering.personregister.mediator.connector.ArbeidssøkerConnector
import no.nav.dagpenger.rapportering.personregister.mediator.connector.RecordKeyResponse
import no.nav.dagpenger.rapportering.personregister.mediator.db.ArbeidssøkerRepository
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.service.ArbeidssøkerService
import no.nav.dagpenger.rapportering.personregister.mediator.utils.arbeidssøkerResponse
import no.nav.dagpenger.rapportering.personregister.mediator.utils.kafka.MockKafkaProducer
import no.nav.dagpenger.rapportering.personregister.mediator.utils.kafka.TestKafkaContainer
import no.nav.dagpenger.rapportering.personregister.mediator.utils.kafka.TestKafkaProducer
import no.nav.dagpenger.rapportering.personregister.modell.Arbeidssøkerperiode
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.paw.arbeidssokerregisteret.api.v1.AvviksType.UKJENT_VERDI
import no.nav.paw.arbeidssokerregisteret.api.v1.Bruker
import no.nav.paw.arbeidssokerregisteret.api.v1.BrukerType.SLUTTBRUKER
import no.nav.paw.arbeidssokerregisteret.api.v1.Metadata
import no.nav.paw.arbeidssokerregisteret.api.v1.Periode
import no.nav.paw.arbeidssokerregisteret.api.v1.TidspunktFraKilde
import no.nav.paw.bekreftelse.paavegneav.v1.PaaVegneAv
import no.nav.paw.bekreftelse.paavegneav.v1.vo.Bekreftelsesloesning.DAGPENGER
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID

class ArbeidssøkerMediatorTest {
    private lateinit var personRepository: PersonRepository
    private lateinit var arbeidssøkerRepository: ArbeidssøkerRepository
    private lateinit var arbeidssøkerConnector: ArbeidssøkerConnector
    private lateinit var overtaBekreftelseKafkaProdusent: MockKafkaProducer<PaaVegneAv>
    private lateinit var arbeidssøkerService: ArbeidssøkerService
    private lateinit var arbeidssøkerMediator: ArbeidssøkerMediator
    private val overtaBekreftelseTopic = "paa_vegne_av"

    @BeforeEach
    fun setup() {
        personRepository = PersonRepositoryFaker()
        arbeidssøkerRepository = ArbeidssøkerRepositoryFaker()
        arbeidssøkerConnector = mockk<ArbeidssøkerConnector>()
        overtaBekreftelseKafkaProdusent = MockKafkaProducer()
        arbeidssøkerService =
            ArbeidssøkerService(
                personRepository,
                arbeidssøkerRepository,
                arbeidssøkerConnector,
                overtaBekreftelseKafkaProdusent,
                overtaBekreftelseTopic,
            )
        arbeidssøkerMediator = ArbeidssøkerMediator(arbeidssøkerService)
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

        with(overtaBekreftelseKafkaProdusent.meldinger) {
            size shouldBe 1
            with(first()) {
                topic() shouldBe overtaBekreftelseTopic
                key() shouldBe recordKey
                value().periodeId shouldBe periodeId
                value().bekreftelsesloesning shouldBe DAGPENGER
            }
        }

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

        with(overtaBekreftelseKafkaProdusent.meldinger) {
            size shouldBe 1
            with(first()) {
                topic() shouldBe overtaBekreftelseTopic
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

        overtaBekreftelseKafkaProdusent.meldinger.size shouldBe 0
    }

    @Test
    fun `utfører ingen operasjoner hvis personen perioden omhandler ikke finnes`() {
        val periode = arbeidssøkerperiode()
        arbeidssøkerMediator.behandle(periode)

        arbeidssøkerRepository.hentArbeidssøkerperioder(person.ident).size shouldBe 0

        overtaBekreftelseKafkaProdusent.meldinger.size shouldBe 0
    }
}

class ArbeidssøkerMediatorMottakTest : ApiTestSetup() {
    // private val arbeidssøkerMediator: ArbeidssøkerMediator = mockk()
    private lateinit var testKafkaContainer: TestKafkaContainer
    private lateinit var testProducer: TestKafkaProducer<Periode>
    private lateinit var testConsumer: KafkaConsumer<Long, Periode>
    private lateinit var kafkaContext: KafkaContext

    private lateinit var personRepository: PersonRepository
    private lateinit var arbeidssøkerRepository: ArbeidssøkerRepository
    private lateinit var arbeidssøkerConnector: ArbeidssøkerConnector
    private lateinit var overtaBekreftelseKafkaProdusent: MockKafkaProducer<PaaVegneAv>
    private lateinit var arbeidssøkerService: ArbeidssøkerService
    private lateinit var arbeidssøkerMediator: ArbeidssøkerMediator
    private val overtaBekreftelseTopic = "paa_vegne_av"

    private val topic = "ARBEIDSSOKERPERIODER_TOPIC"
    private val ident = "12345678910"

    @BeforeEach
    fun setup() {
        personRepository = PersonRepositoryFaker()
        arbeidssøkerRepository = ArbeidssøkerRepositoryFaker()
        arbeidssøkerConnector = mockk<ArbeidssøkerConnector>()
        overtaBekreftelseKafkaProdusent = MockKafkaProducer()
        arbeidssøkerService =
            ArbeidssøkerService(
                personRepository,
                arbeidssøkerRepository,
                arbeidssøkerConnector,
                overtaBekreftelseKafkaProdusent,
                overtaBekreftelseTopic,
            )
        arbeidssøkerMediator = ArbeidssøkerMediator(arbeidssøkerService)

        testKafkaContainer = TestKafkaContainer()
        testProducer = TestKafkaProducer(Periode::class, topic, testKafkaContainer)
        testConsumer = testKafkaContainer.createConsumer()
        kafkaContext =
            KafkaContext(testKafkaContainer.createProducer(PaaVegneAv::class, topic), testConsumer, topic, arbeidssøkerMediator)
    }

    @AfterEach
    fun tearDown() {
        // testKafkaContainer.stop()
        // testKafkaContainer.createProducer(PaaVegneAv::class)
    }

    @Test
    fun nyTest() {
        testApplication {
            println("Setting up test application")

            // val meterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT, PrometheusRegistry.defaultRegistry, Clock.SYSTEM)
            environment {
                println("Setting up env")
                config = mapAppConfig()
            }

            application {
                println("Setting up application")
                testPluginConfiguration(kafkaContext)
            }

            // testConsumer.subscribe(listOf(topic))
            // println("Consumer subscribed to topic: $topic")

            // Wait briefly to ensure the consumer has subscribed before sending the message
            Thread.sleep(1000)

            val metadata =
                Metadata(
                    Instant.now(),
                    Bruker(SLUTTBRUKER, ident),
                    "kilde",
                    "aarsak",
                    TidspunktFraKilde(Instant.now(), UKJENT_VERDI),
                )
            val periode = Periode(UUID.randomUUID(), ident, metadata, null)
            testProducer.send(1234L, periode)

            // Wait for a short period to allow the consumer to consume the message
            Thread.sleep(2000)

            // Poll the consumer to fetch the message
            // val records = testConsumer.poll(Duration.ofSeconds(1))
            // println("Records received: $records")

            verify { arbeidssøkerMediator.behandle(any<ConsumerRecords<Long, Periode>>()) }
        }
    }

    @Test
    fun `kan konsumere arbeidssøkerperiode`() =
        setUpTestApplication(kafkaContext) {
            // testConsumer.subscribe(listOf(topic))

            val metadata =
                Metadata(
                    Instant.now(),
                    Bruker(SLUTTBRUKER, ident),
                    "kilde",
                    "aarsak",
                    TidspunktFraKilde(Instant.now(), UKJENT_VERDI),
                )
            val periode = Periode(UUID.randomUUID(), ident, metadata, null)
            testProducer.send(1234L, periode)

            verify { arbeidssøkerMediator.behandle(any<ConsumerRecords<Long, Periode>>()) }
        }
}

fun Application.testPluginConfiguration(kafkaContext: KafkaContext) {
    println("Installing kafka producer plugin")
    install(KafkaProducerPlugin) {
        println("Installing kafka producer plugin")
        kafkaProducers = listOf(kafkaContext.overtaBekreftelseKafkaProdusent)
    }
    println("Installing kafka consumer plugin")
    install(KafkaConsumerPlugin<Long, Periode>("Arbeidssøkerperioder")) {
        println("Installing kafka consumer plugin")
        this.consumeFunction = kafkaContext.arbeidssøkerMediator::behandle
        // this.errorFunction = kafkaContext.kafkaConsumerExceptionHandler::handleException
        this.kafkaConsumer = kafkaContext.arbeidssøkerperioderKafkaConsumer
        this.kafkaTopics = listOf(kafkaContext.arbeidssøkerperioderTopic)
    }
}
