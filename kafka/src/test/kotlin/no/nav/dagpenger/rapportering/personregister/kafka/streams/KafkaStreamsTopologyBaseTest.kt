package no.nav.dagpenger.rapportering.personregister.kafka.streams

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.mockk
import no.nav.dagpenger.rapportering.personregister.modell.AnnenMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.DagpengerMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.KombinertHendelse
import no.nav.dagpenger.rapportering.personregister.modell.MeldepliktHendelse
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.TestInputTopic
import org.apache.kafka.streams.TestOutputTopic
import org.apache.kafka.streams.TopologyTestDriver
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Properties

/**
 * Base test class for Kafka Streams Topology tests.
 * Provides common setup code and helper methods for creating test data.
 */
abstract class KafkaStreamsTopologyBaseTest {
    protected val objectMapper = ObjectMapper()

    // Test driver and topics (initialized only if useTopologyTestDriver is true)
    protected lateinit var testDriver: TopologyTestDriver
    protected lateinit var meldepliktTopic: TestInputTopic<String, String>
    protected lateinit var meldegruppeTopic: TestInputTopic<String, String>
    protected lateinit var combinedTopic: TestOutputTopic<String, String>
    protected lateinit var unmatchedMeldepliktTopic: TestOutputTopic<String, String>
    protected lateinit var unmatchedMeldegruppeTopic: TestOutputTopic<String, String>

    private val meldepliktTopicName = "meldeplikt-topic"
    private val meldegruppeTopicName = "meldegrupe-topic"

    // Mock handlers (initialized only if useMockHandlers is true)
    protected val meldepliktHandler = mockk<(MeldepliktHendelse) -> Unit>(relaxed = true)
    protected val dagpengerMeldegruppeHander = mockk<(DagpengerMeldegruppeHendelse) -> Unit>(relaxed = true)
    protected val annenMeldegruppeHandler = mockk<(AnnenMeldegruppeHendelse) -> Unit>(relaxed = true)
    protected val kombinertHandler = mockk<(KombinertHendelse) -> Unit>(relaxed = true)

    // Override these properties in subclasses to control setup behavior
    protected open val useTopologyTestDriver: Boolean = false
    protected open val useMockHandlers: Boolean = false

    @BeforeEach
    fun baseSetup() {
        if (useTopologyTestDriver) {
            // Set up the handlers
            val streamHandlers =
                StreamHandlers(
                    meldepliktHandler = meldepliktHandler,
                    dagpengerMeldegruppeHandler = dagpengerMeldegruppeHander,
                    annenMeldegruppeHandler = annenMeldegruppeHandler,
                    kombinertHandler = kombinertHandler,
                )
            // Create test driver
            val props = Properties()
            props["bootstrap.servers"] = "dummy:1234"
            props["application.id"] = "test"
            // Configure state directory to use a specific path instead of OS temp directory
            props["state.dir"] = "build/kafka-streams-test"

            val topology =
                KafkaStreamsTopology(
                    streamHandlers = streamHandlers,
                    meldepliktTopic = meldepliktTopicName,
                    meldegruppeTopic = meldegruppeTopicName,
                ).build()
            testDriver = TopologyTestDriver(topology, props)

            // Create test topics
            meldepliktTopic =
                testDriver.createInputTopic(
                    meldepliktTopicName,
                    Serdes.String().serializer(),
                    Serdes.String().serializer(),
                )

            meldegruppeTopic =
                testDriver.createInputTopic(
                    meldegruppeTopicName,
                    Serdes.String().serializer(),
                    Serdes.String().serializer(),
                )

            combinedTopic =
                testDriver.createOutputTopic(
                    "combined-topic",
                    Serdes.String().deserializer(),
                    Serdes.String().deserializer(),
                )

            unmatchedMeldepliktTopic =
                testDriver.createOutputTopic(
                    "unmatched-meldeplikt-topic",
                    Serdes.String().deserializer(),
                    Serdes.String().deserializer(),
                )

            unmatchedMeldegruppeTopic =
                testDriver.createOutputTopic(
                    "unmatched-meldegruppe-topic",
                    Serdes.String().deserializer(),
                    Serdes.String().deserializer(),
                )
        }

        // Call the subclass setup method
        additionalSetup()
    }

    // Can be overridden by subclasses to add additional setup
    protected open fun additionalSetup() {}

    @AfterEach
    fun baseTearDown() {
        if (useTopologyTestDriver) {
            testDriver.close()
        }

        // Call the subclass teardown method
        additionalTearDown()
    }

    // Can be overridden by subclasses to add additional teardown
    protected open fun additionalTearDown() {}

    // Helper methods to create test data
    protected fun createMeldepliktJson(
        fodselsnr: String,
        hendelseId: String = "meldeplikt-123",
    ): String {
        val jsonNode = objectMapper.createObjectNode()
        val afterNode = objectMapper.createObjectNode()

        afterNode.put("FODSELSNR", fodselsnr)
        afterNode.put("HENDELSE_ID", hendelseId)
        afterNode.put("STATUS_MELDEPLIKT", "J")
        afterNode.put("MELDEPLIKT_ID", 12345)
        afterNode.put("DATO_FRA", "2023-01-01 00:00:00")
        afterNode.put("DATO_TIL", "2023-12-31 23:59:59")
        afterNode.put("HENDELSESDATO", "2023-01-01 00:00:00")

        jsonNode.set<JsonNode>("after", afterNode)

        return objectMapper.writeValueAsString(jsonNode)
    }

    protected fun createMeldegruppeJson(
        fodselsnr: String,
        meldegruppeKode: String,
        hendelseId: String = "meldegruppe-456",
    ): String {
        val jsonNode = objectMapper.createObjectNode()
        val afterNode = objectMapper.createObjectNode()

        afterNode.put("FODSELSNR", fodselsnr)
        afterNode.put("HENDELSE_ID", hendelseId)
        afterNode.put("MELDEGRUPPEKODE", meldegruppeKode)
        afterNode.put("HAR_MELDT_SEG", "J")
        afterNode.put("MELDEGRUPPE_ID", 67890)
        afterNode.put("DATO_FRA", "2023-01-01 00:00:00")
        afterNode.put("DATO_TIL", "2023-12-31 23:59:59")
        afterNode.put("HENDELSESDATO", "2023-01-01 00:00:00")

        jsonNode.set<JsonNode>("after", afterNode)

        return objectMapper.writeValueAsString(jsonNode)
    }

    // Helper extension functions
    protected fun String.arenaDato(): LocalDateTime {
        val pattern = "yyyy-MM-dd HH:mm:ss"
        val formatter = DateTimeFormatter.ofPattern(pattern)
        return LocalDateTime.parse(this, formatter)
    }

    protected fun JsonNode.isMissingOrNull(): Boolean = this.isMissingNode || this.isNull
}
