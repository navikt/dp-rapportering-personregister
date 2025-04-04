package no.nav.dagpenger.rapportering.personregister.kafka.streams

import io.kotest.matchers.shouldBe
import no.nav.dagpenger.rapportering.personregister.modell.AnnenMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.DagpengerMeldegruppeHendelse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Tests for the conversion methods in the KafkaStreamsTopology.
 * Verifies that JSON messages are correctly converted to domain objects.
 */
class KafkaStreamsTopologyConversionTest : KafkaStreamsTopologyBaseTest() {
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
    fun `should convert JSON to MeldepliktHendelse`() {
        // Given
        val fodselsnr = "12345678901"
        val meldepliktJson = createMeldepliktJson(fodselsnr)
        val jsonNode = objectMapper.readTree(meldepliktJson)

        // When
        val meldepliktHendelse = topology.convertToMeldepliktHendelse(jsonNode)

        // Then
        meldepliktHendelse.ident shouldBe fodselsnr
        meldepliktHendelse.referanseId shouldBe "meldeplikt-123"
        meldepliktHendelse.statusMeldeplikt shouldBe true
        meldepliktHendelse.startDato shouldBe "2023-01-01 00:00:00".arenaDato()
        meldepliktHendelse.sluttDato shouldBe "2023-12-31 23:59:59".arenaDato()
    }

    @Test
    fun `should convert JSON to DagpengerMeldegruppeHendelse`() {
        // Given
        val fodselsnr = "12345678901"
        val meldegruppeJson = createMeldegruppeJson(fodselsnr, "DAGP")
        val jsonNode = objectMapper.readTree(meldegruppeJson)

        // When
        val meldegruppeHendelse = topology.convertToMeldegruppeHendelse(jsonNode)

        // Then
        (meldegruppeHendelse is DagpengerMeldegruppeHendelse) shouldBe true
        meldegruppeHendelse.ident shouldBe fodselsnr
        meldegruppeHendelse.referanseId shouldBe "meldegruppe-456"
        (meldegruppeHendelse as DagpengerMeldegruppeHendelse).meldegruppeKode shouldBe "DAGP"
        meldegruppeHendelse.harMeldtSeg shouldBe true
        meldegruppeHendelse.startDato shouldBe "2023-01-01 00:00:00".arenaDato()
        meldegruppeHendelse.sluttDato shouldBe "2023-12-31 23:59:59".arenaDato()
    }

    @Test
    fun `should convert JSON to AnnenMeldegruppeHendelse`() {
        // Given
        val fodselsnr = "12345678901"
        val meldegruppeJson = createMeldegruppeJson(fodselsnr, "ANNEN")
        val jsonNode = objectMapper.readTree(meldegruppeJson)

        // When
        val meldegruppeHendelse = topology.convertToMeldegruppeHendelse(jsonNode)

        // Then
        (meldegruppeHendelse is AnnenMeldegruppeHendelse) shouldBe true
        meldegruppeHendelse.ident shouldBe fodselsnr
        meldegruppeHendelse.referanseId shouldBe "meldegruppe-456"
        (meldegruppeHendelse as AnnenMeldegruppeHendelse).meldegruppeKode shouldBe "ANNEN"
        meldegruppeHendelse.harMeldtSeg shouldBe true
        meldegruppeHendelse.startDato shouldBe "2023-01-01 00:00:00".arenaDato()
        meldegruppeHendelse.sluttDato shouldBe "2023-12-31 23:59:59".arenaDato()
    }

    // Using helper methods from the base class
}
