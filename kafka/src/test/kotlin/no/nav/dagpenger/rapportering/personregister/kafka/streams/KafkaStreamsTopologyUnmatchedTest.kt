package no.nav.dagpenger.rapportering.personregister.kafka.streams

import io.mockk.verify
import no.nav.dagpenger.rapportering.personregister.modell.AnnenMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.DagpengerMeldegruppeHendelse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Tests for handling unmatched messages in the KafkaStreamsTopology.
 * Verifies that unmatched messages are correctly processed by their respective handlers.
 */
class KafkaStreamsTopologyUnmatchedTest : KafkaStreamsTopologyBaseTest() {
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
    fun `should process unmatched meldeplikt message`() {
        // Given
        val fodselsnr = "12345678901"
        val meldepliktJson = createMeldepliktJson(fodselsnr)
        val jsonNode = objectMapper.readTree(meldepliktJson)

        // When - directly call the handler with the converted message
        val meldepliktHendelse = topology.convertToMeldepliktHendelse(jsonNode)
        meldepliktHandler.invoke(meldepliktHendelse)

        // Then
        verify(exactly = 1) { meldepliktHandler.invoke(any()) }
    }

    @Test
    fun `should process unmatched meldegruppe message with DAGP code`() {
        // Given
        val fodselsnr = "12345678901"
        val meldegruppeJson = createMeldegruppeJson(fodselsnr, "DAGP")
        val jsonNode = objectMapper.readTree(meldegruppeJson)

        // When - directly call the handler with the converted message
        val meldegruppeHendelse = topology.convertToMeldegruppeHendelse(jsonNode)
        dagpengerMeldegruppeHander.invoke(meldegruppeHendelse as DagpengerMeldegruppeHendelse)

        // Then
        verify(exactly = 1) { dagpengerMeldegruppeHander.invoke(any()) }
    }

    @Test
    fun `should process unmatched meldegruppe message with ANNEN code`() {
        // Given
        val fodselsnr = "12345678901"
        val meldegruppeJson = createMeldegruppeJson(fodselsnr, "ANNEN")
        val jsonNode = objectMapper.readTree(meldegruppeJson)

        // When - directly call the handler with the converted message
        val meldegruppeHendelse = topology.convertToMeldegruppeHendelse(jsonNode)
        annenMeldegruppeHandler.invoke(meldegruppeHendelse as AnnenMeldegruppeHendelse)

        // Then
        verify(exactly = 1) { annenMeldegruppeHandler.invoke(any()) }
    }
}
