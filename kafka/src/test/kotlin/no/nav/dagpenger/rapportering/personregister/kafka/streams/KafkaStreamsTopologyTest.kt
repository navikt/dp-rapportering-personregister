package no.nav.dagpenger.rapportering.personregister.kafka.streams

import com.fasterxml.jackson.databind.JsonNode
import io.kotest.matchers.shouldBe
import io.mockk.slot
import io.mockk.verify
import no.nav.dagpenger.rapportering.personregister.modell.DagpengerMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.Hendelse
import no.nav.dagpenger.rapportering.personregister.modell.KombinertHendelse
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Duration

/**
 * Tests for the basic join functionality of the KafkaStreamsTopology.
 * Verifies that messages with matching FODSELSNR are correctly joined and processed.
 */
class KafkaStreamsTopologyTest : KafkaStreamsTopologyBaseTest() {
    // Override to enable both test driver and mock handlers
    override val useTopologyTestDriver: Boolean = true
    override val useMockHandlers: Boolean = true

    @Nested
    inner class JoinTests {
        @Test
        fun `should join messages with matching FODSELSNR`() {
            // Given
            val fodselsnr = "12345678901"
            val annetFodselsnr = "98765432109"
            val meldepliktJson = createMeldepliktJson(fodselsnr)
            val meldegruppeJson = createMeldegruppeJson(fodselsnr, "DAGP")
            val meldegruppeMedAnnetFodselsnrJson = createMeldegruppeJson(annetFodselsnr, "DAGP")

            // When
            meldepliktTopic.pipeInput(fodselsnr, meldepliktJson)
            meldegruppeTopic.pipeInput(fodselsnr, meldegruppeJson)
            meldegruppeTopic.pipeInput(annetFodselsnr, meldegruppeMedAnnetFodselsnrJson)

            // Then
            val combinedMessages = combinedTopic.readRecordsToList()
            combinedMessages.size shouldBe 1

            val combinedMessage = objectMapper.readTree(combinedMessages[0].value)
            combinedMessage.path("@event_name").asText() shouldBe "combined_message"
            combinedMessage.path("fodselsnr").asText() shouldBe fodselsnr
            combinedMessage.has("meldeplikt") shouldBe true
            combinedMessage.has("meldegruppe") shouldBe true

            // Verify that the kombinertHandler was called
            val kombinertSlot = slot<KombinertHendelse>()
            verify { kombinertHandler.invoke(capture(kombinertSlot)) }

            val kombinertHendelse = kombinertSlot.captured
            kombinertHendelse.ident shouldBe fodselsnr
            kombinertHendelse.meldepliktHendelser.size shouldBe 1
            kombinertHendelse.meldegruppeHendelser.size shouldBe 1
        }
    }

    // Helper methods to create test data
    private fun createMeldepliktJson(fodselsnr: String): String {
        val jsonNode = objectMapper.createObjectNode()
        val afterNode = objectMapper.createObjectNode()

        afterNode.put("FODSELSNR", fodselsnr)
        afterNode.put("HENDELSE_ID", "meldeplikt-123")
        afterNode.put("STATUS_MELDEPLIKT", "J")
        afterNode.put("MELDEPLIKT_ID", 12345)
        afterNode.put("DATO_FRA", "2023-01-01 00:00:00")
        afterNode.put("DATO_TIL", "2023-12-31 23:59:59")
        afterNode.put("HENDELSESDATO", "2023-01-01 00:00:00")

        jsonNode.set<JsonNode>("after", afterNode)

        return objectMapper.writeValueAsString(jsonNode)
    }

    private fun createMeldegruppeJson(
        fodselsnr: String,
        meldegruppeKode: String,
    ): String {
        val jsonNode = objectMapper.createObjectNode()
        val afterNode = objectMapper.createObjectNode()

        afterNode.put("FODSELSNR", fodselsnr)
        afterNode.put("HENDELSE_ID", "meldegruppe-456")
        afterNode.put("MELDEGRUPPEKODE", meldegruppeKode)
        afterNode.put("HAR_MELDT_SEG", "J")
        afterNode.put("MELDEGRUPPE_ID", 67890)
        afterNode.put("DATO_FRA", "2023-01-01 00:00:00")
        afterNode.put("DATO_TIL", "2023-12-31 23:59:59")
        afterNode.put("HENDELSESDATO", "2023-01-01 00:00:00")

        jsonNode.set<JsonNode>("after", afterNode)

        return objectMapper.writeValueAsString(jsonNode)
    }
}
