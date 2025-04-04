package no.nav.dagpenger.rapportering.personregister.kafka.streams

import com.fasterxml.jackson.databind.JsonNode
import io.kotest.matchers.shouldBe
import io.mockk.slot
import io.mockk.verify
import no.nav.dagpenger.rapportering.personregister.modell.AnnenMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.DagpengerMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.KombinertHendelse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Tests for the combineMessages method in the KafkaStreamsTopology.
 * Verifies that messages are correctly combined and processed.
 */
class KafkaStreamsTopologyCombineMessagesTest : KafkaStreamsTopologyBaseTest() {
    // Override to enable mock handlers but not test driver
    override val useMockHandlers: Boolean = true
    private lateinit var streamHandler: StreamHandlers
    private lateinit var topology: KafkaStreamsTopology

    @BeforeEach
    override fun additionalSetup() {
        // Override the handlers to only use kombinertHandler
        streamHandler =
            StreamHandlers(
                meldepliktHandler = meldepliktHandler,
                dagpengerMeldegruppeHandler = dagpengerMeldegruppeHander,
                annenMeldegruppeHandler = annenMeldegruppeHandler,
                kombinertHandler = kombinertHandler,
            )
        topology =
            KafkaStreamsTopology(
                streamHandlers = streamHandler,
                meldepliktTopic = "meldeplikt-topic",
                meldegruppeTopic = "meldegruppe-topic",
            )
    }

    @Test
    fun `should combine meldeplikt and meldegruppe messages with DAGP code`() {
        // Given
        val fodselsnr = "12345678901"
        val meldepliktJson = createMeldepliktJson(fodselsnr)
        val meldegruppeJson = createMeldegruppeJson(fodselsnr, "DAGP")

        // When
        val combinedJson = topology.combineMessages(meldepliktJson, meldegruppeJson)

        // Then
        val combinedNode = objectMapper.readTree(combinedJson)
        combinedNode.path("@event_name").asText() shouldBe "combined_message"
        combinedNode.path("fodselsnr").asText() shouldBe fodselsnr
        combinedNode.has("meldeplikt") shouldBe true
        combinedNode.has("meldegruppe") shouldBe true

        // Verify that the kombinertHandler was called
        val kombinertSlot = slot<KombinertHendelse>()
        verify { kombinertHandler.invoke(capture(kombinertSlot)) }

        val kombinertHendelse = kombinertSlot.captured
        kombinertHendelse.ident shouldBe fodselsnr
        kombinertHendelse.meldepliktHendelser.size shouldBe 1
        kombinertHendelse.meldegruppeHendelser.size shouldBe 1
        (kombinertHendelse.meldegruppeHendelser[0] is DagpengerMeldegruppeHendelse) shouldBe true
    }

    @Test
    fun `should combine meldeplikt and meldegruppe messages with ANNEN code`() {
        // Given
        val fodselsnr = "12345678901"
        val meldepliktJson = createMeldepliktJson(fodselsnr)
        val meldegruppeJson = createMeldegruppeJson(fodselsnr, "ANNEN")

        // When
        val combinedJson = topology.combineMessages(meldepliktJson, meldegruppeJson)

        // Then
        val combinedNode = objectMapper.readTree(combinedJson)
        combinedNode.path("@event_name").asText() shouldBe "combined_message"
        combinedNode.path("fodselsnr").asText() shouldBe fodselsnr
        combinedNode.has("meldeplikt") shouldBe true
        combinedNode.has("meldegruppe") shouldBe true

        // Verify that the kombinertHandler was called
        val kombinertSlot = slot<KombinertHendelse>()
        verify { kombinertHandler.invoke(capture(kombinertSlot)) }

        val kombinertHendelse = kombinertSlot.captured
        kombinertHendelse.ident shouldBe fodselsnr
        kombinertHendelse.meldepliktHendelser.size shouldBe 1
        kombinertHendelse.meldegruppeHendelser.size shouldBe 1
        (kombinertHendelse.meldegruppeHendelser[0] is AnnenMeldegruppeHendelse) shouldBe true
    }

    @Test
    fun `should combine meldegruppe and meldeplikt messages with DAGP code`() {
        // Given
        val fodselsnr = "12345678901"
        val meldepliktJson = createMeldepliktJson(fodselsnr)
        val meldegruppeJson = createMeldegruppeJson(fodselsnr, "DAGP")

        // When
        val combinedJson = topology.combineMessages(meldepliktJson, meldegruppeJson)

        // Then
        val combinedNode = objectMapper.readTree(combinedJson)
        combinedNode.path("@event_name").asText() shouldBe "combined_message"
        combinedNode.path("fodselsnr").asText() shouldBe fodselsnr
        combinedNode.has("meldeplikt") shouldBe true
        combinedNode.has("meldegruppe") shouldBe true

        // Verify that the kombinertHandler was called
        val kombinertSlot = slot<KombinertHendelse>()
        verify { kombinertHandler.invoke(capture(kombinertSlot)) }

        val kombinertHendelse = kombinertSlot.captured
        kombinertHendelse.ident shouldBe fodselsnr
        kombinertHendelse.meldepliktHendelser.size shouldBe 1
        kombinertHendelse.meldegruppeHendelser.size shouldBe 1
        (kombinertHendelse.meldegruppeHendelser[0] is DagpengerMeldegruppeHendelse) shouldBe true
    }

    @Test
    fun `should combine meldegruppe and meldeplikt messages with ANNEN code`() {
        // Given
        val fodselsnr = "12345678901"
        val meldepliktJson = createMeldepliktJson(fodselsnr)
        val meldegruppeJson = createMeldegruppeJson(fodselsnr, "ANNEN")

        // When
        val combinedJson = topology.combineMessages(meldepliktJson, meldegruppeJson)

        // Then
        val combinedNode = objectMapper.readTree(combinedJson)
        combinedNode.path("@event_name").asText() shouldBe "combined_message"
        combinedNode.path("fodselsnr").asText() shouldBe fodselsnr
        combinedNode.has("meldeplikt") shouldBe true
        combinedNode.has("meldegruppe") shouldBe true

        // Verify that the kombinertHandler was called
        val kombinertSlot = slot<KombinertHendelse>()
        verify { kombinertHandler.invoke(capture(kombinertSlot)) }

        val kombinertHendelse = kombinertSlot.captured
        kombinertHendelse.ident shouldBe fodselsnr
        kombinertHendelse.meldepliktHendelser.size shouldBe 1
        kombinertHendelse.meldegruppeHendelser.size shouldBe 1
        (kombinertHendelse.meldegruppeHendelser[0] is AnnenMeldegruppeHendelse) shouldBe true
    }

    @Test
    fun `should throw exception when meldeplikt JSON is malformed`() {
        // Given
        val fodselsnr = "12345678901"
        val meldepliktJson = "{ invalid json }"
        val meldegruppeJson = createMeldegruppeJson(fodselsnr, "DAGP")

        // When & Then
        assertThrows<Exception> {
            topology.combineMessages(meldepliktJson, meldegruppeJson)
        }
    }

    @Test
    fun `should throw exception when meldegruppe JSON is malformed`() {
        // Given
        val fodselsnr = "12345678901"
        val meldepliktJson = createMeldepliktJson(fodselsnr)
        val meldegruppeJson = "{ invalid json }"

        // When & Then
        assertThrows<Exception> {
            topology.combineMessages(meldepliktJson, meldegruppeJson)
        }
    }

    @Test
    fun `should handle meldeplikt JSON with missing required fields`() {
        // Given
        val fodselsnr = "12345678901"
        val meldepliktJson = createMeldepliktJsonWithMissingFields(fodselsnr)
        val meldegruppeJson = createMeldegruppeJson(fodselsnr, "DAGP")

        // When
        val combinedJson = topology.combineMessages(meldepliktJson, meldegruppeJson)

        // Then
        val combinedNode = objectMapper.readTree(combinedJson)
        combinedNode.path("@event_name").asText() shouldBe "combined_message"
        combinedNode.path("fodselsnr").asText() shouldBe fodselsnr
        combinedNode.has("meldeplikt") shouldBe true
        combinedNode.has("meldegruppe") shouldBe true

        // Verify that the kombinertHandler was called
        val kombinertSlot = slot<KombinertHendelse>()
        verify { kombinertHandler.invoke(capture(kombinertSlot)) }

        val kombinertHendelse = kombinertSlot.captured
        kombinertHendelse.ident shouldBe fodselsnr
        kombinertHendelse.meldepliktHendelser.size shouldBe 1
        kombinertHendelse.meldegruppeHendelser.size shouldBe 1
    }

    @Test
    fun `should handle meldegruppe JSON with missing required fields`() {
        // Given
        val fodselsnr = "12345678901"
        val meldepliktJson = createMeldepliktJson(fodselsnr)
        val meldegruppeJson = createMeldegruppeJsonWithMissingFields(fodselsnr)

        // When
        val combinedJson = topology.combineMessages(meldepliktJson, meldegruppeJson)

        // Then
        val combinedNode = objectMapper.readTree(combinedJson)
        combinedNode.path("@event_name").asText() shouldBe "combined_message"
        combinedNode.path("fodselsnr").asText() shouldBe fodselsnr
        combinedNode.has("meldeplikt") shouldBe true
        combinedNode.has("meldegruppe") shouldBe true

        // Verify that the kombinertHandler was called
        val kombinertSlot = slot<KombinertHendelse>()
        verify { kombinertHandler.invoke(capture(kombinertSlot)) }

        val kombinertHendelse = kombinertSlot.captured
        kombinertHendelse.ident shouldBe fodselsnr
        kombinertHendelse.meldepliktHendelser.size shouldBe 1
        kombinertHendelse.meldegruppeHendelser.size shouldBe 1
    }

    @Test
    fun `should throw exception when date format is incorrect`() {
        // Given
        val fodselsnr = "12345678901"
        val meldepliktJson = createMeldepliktJsonWithIncorrectDateFormat(fodselsnr)
        val meldegruppeJson = createMeldegruppeJson(fodselsnr, "DAGP")

        // When & Then
        assertThrows<Exception> {
            topology.combineMessages(meldepliktJson, meldegruppeJson)
        }
    }

    // Helper methods for creating test data with special conditions
    private fun createMeldepliktJsonWithMissingFields(fodselsnr: String): String {
        val jsonNode = objectMapper.createObjectNode()
        val afterNode = objectMapper.createObjectNode()

        // Missing required fields: HENDELSE_ID, STATUS_MELDEPLIKT
        afterNode.put("FODSELSNR", fodselsnr)
        afterNode.put("MELDEPLIKT_ID", 12345)
        afterNode.put("DATO_FRA", "2023-01-01 00:00:00")
        afterNode.put("DATO_TIL", "2023-12-31 23:59:59")
        afterNode.put("HENDELSESDATO", "2023-01-01 00:00:00")

        jsonNode.set<JsonNode>("after", afterNode)

        return objectMapper.writeValueAsString(jsonNode)
    }

    private fun createMeldegruppeJsonWithMissingFields(fodselsnr: String): String {
        val jsonNode = objectMapper.createObjectNode()
        val afterNode = objectMapper.createObjectNode()

        // Missing required fields: HENDELSE_ID, MELDEGRUPPEKODE
        afterNode.put("FODSELSNR", fodselsnr)
        afterNode.put("HAR_MELDT_SEG", "J")
        afterNode.put("MELDEGRUPPE_ID", 67890)
        afterNode.put("DATO_FRA", "2023-01-01 00:00:00")
        afterNode.put("DATO_TIL", "2023-12-31 23:59:59")
        afterNode.put("HENDELSESDATO", "2023-01-01 00:00:00")

        jsonNode.set<JsonNode>("after", afterNode)

        return objectMapper.writeValueAsString(jsonNode)
    }

    private fun createMeldepliktJsonWithIncorrectDateFormat(fodselsnr: String): String {
        val jsonNode = objectMapper.createObjectNode()
        val afterNode = objectMapper.createObjectNode()

        afterNode.put("FODSELSNR", fodselsnr)
        afterNode.put("HENDELSE_ID", "meldeplikt-123")
        afterNode.put("STATUS_MELDEPLIKT", "J")
        afterNode.put("MELDEPLIKT_ID", 12345)
        // Incorrect date format: should be "yyyy-MM-dd HH:mm:ss"
        afterNode.put("DATO_FRA", "01/01/2023")
        afterNode.put("DATO_TIL", "31/12/2023")
        afterNode.put("HENDELSESDATO", "01/01/2023")

        jsonNode.set<JsonNode>("after", afterNode)

        return objectMapper.writeValueAsString(jsonNode)
    }
}
