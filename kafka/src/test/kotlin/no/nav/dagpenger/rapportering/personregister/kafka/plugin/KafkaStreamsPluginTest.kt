package no.nav.dagpenger.rapportering.personregister.kafka.plugin

import io.mockk.mockk
import io.mockk.unmockkAll
import no.nav.dagpenger.rapportering.personregister.kafka.streams.KafkaStreamsTopology
import no.nav.dagpenger.rapportering.personregister.modell.AnnenMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.DagpengerMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.KombinertHendelse
import no.nav.dagpenger.rapportering.personregister.modell.MeldepliktHendelse
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

class KafkaStreamsPluginTest {
    private val mockMeldepliktHandler = mockk<(MeldepliktHendelse) -> Unit>(relaxed = true)
    private val mockdagpengerMeldegruppeHandler = mockk<(DagpengerMeldegruppeHendelse) -> Unit>(relaxed = true)
    private val mockAnnenMeldegruppeHandler = mockk<(AnnenMeldegruppeHendelse) -> Unit>(relaxed = true)
    private val mockKombinertHandler = mockk<(KombinertHendelse) -> Unit>(relaxed = true)

    @BeforeEach
    fun setup() {
        mockk<KafkaStreamsTopology>(relaxed = true)
        // every { KafkaStreamsTopology.setHandlers(any(), any(), any(), any()) } returns Unit
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    /*@Test
    fun `should configure handlers correctly`() {
        val streamHandlers =
            StreamHandlers(
                meldepliktHandler = mockMeldepliktHandler,
                dagpengerMeldegruppeHandler = mockdagpengerMeldegruppeHandler,
                annenMeldegruppeHandler = mockAnnenMeldegruppeHandler,
                kombinertHandler = mockKombinertHandler,
            )
        val topology =
            KafkaStreamsTopology(
                streamHandlers = streamHandlers,
                meldepliktTopic = "meldeplikt-topic",
                meldegruppeTopic = "meldegruppe-topic",
            ).build()
        val streams = KafkaStreams(topology, mockk<Properties>(relaxed = true))
        // Given
        val config =
            KafkaStreamsPluginConfig().apply { this.streams = streams }

        // When & Then
        assertDoesNotThrow {
            // Just verify that we can call this method without errors
            KafkaStreamsTopology.setHandlers(
                meldepliktHandler = config.meldepliktHandler,
                dagpengerMeldegruppeHandler = config.dagpengerMeldegruppeHandler,
                annenMeldegruppeHandler = config.annenMeldegruppeHandler,
                kombinertHandler = config.kombinertHandler,
            )
        }

        // Verify the method was called with the correct parameters
        verify {
            KafkaStreamsTopology.setHandlers(
                meldepliktHandler = config.meldepliktHandler,
                dagpengerMeldegruppeHandler = config.dagpengerMeldegruppeHandler,
                annenMeldegruppeHandler = config.annenMeldegruppeHandler,
                kombinertHandler = config.kombinertHandler,
            )
        }
    }*/
}
