package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.rapportering.personregister.mediator.PersonstatusMediator
import no.nav.dagpenger.rapportering.personregister.mediator.hendelser.ArbeidssøkerHendelse
import no.nav.dagpenger.rapportering.personregister.mediator.kafka.TestKafkaContainer
import no.nav.dagpenger.rapportering.personregister.mediator.kafka.TestKafkaProducer
import no.nav.dagpenger.rapportering.personregister.mediator.kafka.consumer.KafkaMessageConsumer
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration

class ArbeidssøkerperiodeMottakTest {
    private val topic = "paw.arbeidssokerperioder-v1"
    private lateinit var testKafkaContainer: TestKafkaContainer

    private val personstatusMediator: PersonstatusMediator = mockk(relaxed = true)
    private lateinit var testProducer: TestKafkaProducer<String>
    private lateinit var testConsumer: KafkaConsumer<String, String>
    private lateinit var kafkaMessageConsumer: KafkaMessageConsumer

    @BeforeEach
    fun setup() {
        testKafkaContainer = TestKafkaContainer()
        testProducer = TestKafkaProducer(topic, testKafkaContainer)
        testConsumer = testKafkaContainer.createConsumer(topic)
        kafkaMessageConsumer =
            KafkaMessageConsumer(
                kafkaConsumer = testConsumer,
                topic = topic,
                pollTimeoutInSeconds = Duration.ofSeconds(1),
            ).apply {
                register(ArbeidssøkerperiodeMottak(personstatusMediator))
            }
    }

    @AfterEach
    fun tearDown() {
        kafkaMessageConsumer.stop()
        testKafkaContainer.stop()
    }

    @Test
    fun `skal håndtere arbeidssøkerperiode-melding`() =
        runBlocking {
            kafkaMessageConsumer.start()

            testProducer.send("key1", gyldigArbeidssøkerperiode)

            delay(2000)
            verify(exactly = 1) { personstatusMediator.behandle(ofType<ArbeidssøkerHendelse>()) }

            kafkaMessageConsumer.stop()
        }

    @Test
    fun `skal håndtere ugyldig arbeidssøkerperiode-melding`() =
        runBlocking {
            kafkaMessageConsumer.start()

            testProducer.send("key1", ugylidaAbeidssøkerperiode)

            delay(2000)
            verify(exactly = 0) { personstatusMediator.behandle(ofType<ArbeidssøkerHendelse>()) }

            kafkaMessageConsumer.stop()
        }
}

val gyldigArbeidssøkerperiode =
    """
    {
      "id": "123e4567-e89b-12d3-a456-426614174000",
      "identitetsnummer": "12345678910",
      "startet": {
        "tidspunkt": 1672531200000,
        "utfoertAv": {
          "id": "system-user",
          "type": "SYSTEM"
        },
        "kilde": "system-api",
        "aarsak": "INITIAL_REGISTRATION",
        "tidspunktFraKilde": {
          "tidspunkt": 1672531200000,
          "avviksType": "EPOCH_MILLIS"
        }
      },
      "avsluttet": null
    }
    """.trimIndent()

val ugylidaAbeidssøkerperiode =
    """
    {
      "id": 123e4567, // Invalid JSON
      "identitetsnummer": "missing-brace"
    """.trimIndent()
