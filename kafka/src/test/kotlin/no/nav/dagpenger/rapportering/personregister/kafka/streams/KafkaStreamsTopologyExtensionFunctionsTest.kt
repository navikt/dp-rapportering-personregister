package no.nav.dagpenger.rapportering.personregister.kafka.streams

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.MissingNode
import com.fasterxml.jackson.databind.node.NullNode
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime
import java.time.format.DateTimeParseException

/**
 * Tests for the extension functions in the KafkaStreamsTopology.
 * Verifies that the helper methods work correctly.
 */
class KafkaStreamsTopologyExtensionFunctionsTest : KafkaStreamsTopologyBaseTest() {

    @Test
    fun `isMissingOrNull should return true for missing node`() {
        // Given
        val missingNode: JsonNode = MissingNode.getInstance()

        // When & Then
        missingNode.isMissingOrNull() shouldBe true
    }

    @Test
    fun `isMissingOrNull should return true for null node`() {
        // Given
        val nullNode: JsonNode = NullNode.getInstance()

        // When & Then
        nullNode.isMissingOrNull() shouldBe true
    }

    @Test
    fun `isMissingOrNull should return false for non-null node`() {
        // Given
        val node: JsonNode = objectMapper.createObjectNode().put("test", "value")

        // When & Then
        node.isMissingOrNull() shouldBe false
    }

    @Test
    fun `arenaDato should parse valid date format`() {
        // Given
        val dateString = "2023-01-01 00:00:00"

        // When
        val result = dateString.arenaDato()

        // Then
        result shouldBe LocalDateTime.of(2023, 1, 1, 0, 0, 0)
    }

    @Test
    fun `arenaDato should throw exception for invalid date format`() {
        // Given
        val dateString = "01/01/2023"

        // When & Then
        assertThrows<DateTimeParseException> {
            dateString.arenaDato()
        }
    }
}