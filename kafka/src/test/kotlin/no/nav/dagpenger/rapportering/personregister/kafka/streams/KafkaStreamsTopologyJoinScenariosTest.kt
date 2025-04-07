package no.nav.dagpenger.rapportering.personregister.kafka.streams

import io.kotest.matchers.shouldBe
import io.mockk.slot
import io.mockk.verify
import no.nav.dagpenger.rapportering.personregister.modell.KombinertHendelse
import org.junit.jupiter.api.Test

/**
 * Tests for additional join scenarios in the KafkaStreamsTopology.
 * Verifies behavior when messages arrive in different orders or with different characteristics.
 */
class KafkaStreamsTopologyJoinScenariosTest : KafkaStreamsTopologyBaseTest() {
    // Override to enable both test driver and mock handlers
    override val useTopologyTestDriver: Boolean = true
    override val useMockHandlers: Boolean = true

    @Test
    fun `should join messages when they arrive in reverse order`() {
        // Given
        val fodselsnr = "12345678901"
        val meldepliktJson = createMeldepliktJson(fodselsnr)
        val meldegruppeJson = createMeldegruppeJson(fodselsnr, "DAGP")

        // When - send meldegruppe message first, then meldeplikt message
        meldegruppeTopic.pipeInput(fodselsnr, meldegruppeJson)
        meldepliktTopic.pipeInput(fodselsnr, meldepliktJson)

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
