package no.nav.dagpenger.rapportering.personregister.mediator.kafka

import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.rapportering.personregister.mediator.TestTopicMediator
import no.nav.dagpenger.rapportering.personregister.mediator.hendelser.TestTopicHendelse
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class TestTopicConsumerTest {
    private val topic = "test-topic"
    private var testKafkaContainer: TestKafkaContainer = TestKafkaContainer()

    private var testTopicMediator: TestTopicMediator = mockk(relaxed = true)
    private val testProducer = TestKafkaProducer<TestTopicHendelse>( topic, testKafkaContainer)
    private var consumer: KafkaConsumer<String, String> = testKafkaContainer.createConsumer()

    @AfterEach
    fun tearDown() {
        testKafkaContainer.stop()
    }

    @Test
    fun `should process user registration messages`() =
        runBlocking {
            val testTopicConsumer =
                TestTopicConsumer(
                    kafkaConsumer = consumer,
                    topic = topic,
                    mediator = testTopicMediator,
                ).apply { stream() }
            testProducer.send("key1", testTopicEvent)

            delay(1000) // Vent for prosessering. TODO: Bytt ut med Deferred

            testTopicConsumer.stop()

            verify(exactly = 1) { testTopicMediator.behandle(any()) }
        }

    companion object {
        private val testTopicEvent =
            // language=JSON
            """
            {
              "periodeId": "periode123",
              "bekreftelsesLøsning": "DAGPENGER",
              "start": {
                  "intervalMS": "1209600000",
                  "graceMS": "691200000"
              }
            }
            """.trimIndent()
    }
}
