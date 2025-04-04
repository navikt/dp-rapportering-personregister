package no.nav.dagpenger.rapportering.personregister.kafka.streams

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.modell.AnnenMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.DagpengerMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.Hendelse
import no.nav.dagpenger.rapportering.personregister.modell.KombinertHendelse
import no.nav.dagpenger.rapportering.personregister.modell.MeldepliktHendelse
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.Topology
import org.apache.kafka.streams.kstream.Consumed
import org.apache.kafka.streams.kstream.JoinWindows
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.Produced
import org.apache.kafka.streams.kstream.StreamJoined
import org.apache.kafka.streams.kstream.ValueJoiner
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val logger = KotlinLogging.logger {}
private val objectMapper = ObjectMapper()

data class StreamHandlers(
    val meldepliktHandler: ((MeldepliktHendelse) -> Unit),
    val dagpengerMeldegruppeHandler: ((DagpengerMeldegruppeHendelse) -> Unit),
    val annenMeldegruppeHandler: ((AnnenMeldegruppeHendelse) -> Unit),
    val kombinertHandler: ((KombinertHendelse) -> Unit),
)

/**
 * Creates a Kafka Streams topology that joins two topics based on FODSELSNR.
 * If a message is received on one topic, it waits for a matching message on the other topic for up to 1 minute.
 * If no matching message is found within 1 minute, the message is processed individually.
 */
class KafkaStreamsTopology(
    private val streamHandlers: StreamHandlers,
    private val meldepliktTopic: String,
    private val meldegruppeTopic: String,
    private val combinedTopic: String = "combined-topic",
    private val unmatchedMeldepliktTopic: String = "unmatched-meldeplikt-topic",
    private val unmatchedMeldegruppeTopic: String = "unmatched-meldegruppe-topic",
) {
    private val joinWindowDuration = Duration.ofMinutes(1)

    fun build(): Topology {
        val builder = StreamsBuilder()

        // Define the input streams
        val meldepliktStream: KStream<String, String> =
            builder.stream(
                meldepliktTopic,
                Consumed.with(Serdes.String(), Serdes.String()),
            )

        val meldegruppeStream: KStream<String, String> =
            builder.stream(
                meldegruppeTopic,
                Consumed.with(Serdes.String(), Serdes.String()),
            )

        // Extract FODSELSNR as the key for joining
        val keyedMeldepliktStream =
            meldepliktStream.selectKey { _, value ->
                try {
                    val jsonNode = objectMapper.readTree(value)
                    jsonNode.path("after").path("FODSELSNR").asText()
                } catch (e: Exception) {
                    logger.error(e) { "Failed to extract FODSELSNR from meldeplikt message: $value" }
                    "INVALID_KEY"
                }
            }

        val keyedMeldegruppeStream =
            meldegruppeStream.selectKey { _, value ->
                try {
                    val jsonNode = objectMapper.readTree(value)
                    jsonNode.path("after").path("FODSELSNR").asText()
                } catch (e: Exception) {
                    logger.error(e) { "Failed to extract FODSELSNR from meldegruppe message: $value" }
                    "INVALID_KEY"
                }
            }

        // Join the streams with a 1-minute window
        val joinedStream =
            keyedMeldepliktStream.join(
                keyedMeldegruppeStream,
                ValueJoiner<String, String, String> { meldepliktValue, meldegruppeValue ->
                    combineMessages(meldepliktValue, meldegruppeValue)
                },
                JoinWindows.ofTimeDifferenceWithNoGrace(joinWindowDuration),
                StreamJoined.with(Serdes.String(), Serdes.String(), Serdes.String()),
            )

        // Send joined messages to the combined topic
        joinedStream.to(combinedTopic, Produced.with(Serdes.String(), Serdes.String()))

        // Handle unmatched messages after the join window expires
        val leftJoinedStream =
            keyedMeldepliktStream.leftJoin(
                keyedMeldegruppeStream,
                ValueJoiner<String, String, Pair<String, String?>> { meldepliktValue, meldegruppeValue ->
                    Pair(meldepliktValue, meldegruppeValue)
                },
                JoinWindows.ofTimeDifferenceWithNoGrace(joinWindowDuration),
                StreamJoined.with(Serdes.String(), Serdes.String(), Serdes.String()),
            )

        // Filter out messages that were successfully joined
        val unmatchedMeldepliktStream =
            leftJoinedStream
                .filter { _, pair -> pair.second == null }
                .mapValues { _, pair -> pair.first }

        // Process unmatched meldeplikt messages
        unmatchedMeldepliktStream.foreach { _, value ->
            try {
                val jsonNode = objectMapper.readTree(value)
                val meldepliktHendelse = convertToMeldepliktHendelse(jsonNode)
                streamHandlers.meldepliktHandler.invoke(meldepliktHendelse)
            } catch (e: Exception) {
                logger.error(e) { "Failed to process unmatched meldeplikt message: $value" }
            }
        }

        unmatchedMeldepliktStream.to(unmatchedMeldepliktTopic, Produced.with(Serdes.String(), Serdes.String()))

        // Do the same for meldegruppe messages
        val rightJoinedStream =
            keyedMeldegruppeStream.leftJoin(
                keyedMeldepliktStream,
                ValueJoiner<String, String, Pair<String, String?>> { meldegruppeValue, meldepliktValue ->
                    Pair(meldegruppeValue, meldepliktValue)
                },
                JoinWindows.ofTimeDifferenceWithNoGrace(joinWindowDuration),
                StreamJoined.with(Serdes.String(), Serdes.String(), Serdes.String()),
            )

        val unmatchedMeldegruppeStream =
            rightJoinedStream
                .filter { _, pair -> pair.second == null }
                .mapValues { _, pair -> pair.first }

        // Process unmatched meldegruppe messages
        unmatchedMeldegruppeStream.foreach { _, value ->
            try {
                val jsonNode = objectMapper.readTree(value)
                val meldegruppeHendelse = convertToMeldegruppeHendelse(jsonNode)
                when (meldegruppeHendelse) {
                    is DagpengerMeldegruppeHendelse -> streamHandlers.dagpengerMeldegruppeHandler.invoke(meldegruppeHendelse)
                    is AnnenMeldegruppeHendelse -> streamHandlers.annenMeldegruppeHandler.invoke(meldegruppeHendelse)
                }
            } catch (e: Exception) {
                logger.error(e) { "Failed to process unmatched meldegruppe message: $value" }
            }
        }

        unmatchedMeldegruppeStream.to(unmatchedMeldegruppeTopic, Produced.with(Serdes.String(), Serdes.String()))

        return builder.build()
    }

    /**
     * Combines meldeplikt and meldegruppe messages into a single message.
     */
    fun combineMessages(
        meldepliktJson: String,
        meldegruppeJson: String,
    ): String {
        try {
            val meldepliktNode = objectMapper.readTree(meldepliktJson)
            val meldegruppeNode = objectMapper.readTree(meldegruppeJson)

            // Convert to Hendelse objects
            val meldepliktHendelse = convertToMeldepliktHendelse(meldepliktNode)
            val meldegruppeHendelse = convertToMeldegruppeHendelse(meldegruppeNode)

            // Create a KombinertHendelse
            val kombinertHendelse =
                KombinertHendelse(
                    ident = meldepliktHendelse.ident,
                    dato = LocalDateTime.now(),
                    referanseId = "${meldepliktHendelse.referanseId}-${meldegruppeHendelse.referanseId}",
                    meldepliktHendelser = listOf(meldepliktHendelse),
                    meldegruppeHendelser = listOf(meldegruppeHendelse),
                )

            // Call the kombinertHandler if it's set
            streamHandlers.kombinertHandler.invoke(kombinertHendelse)

            // Create a combined message for Kafka
            val combinedNode = objectMapper.createObjectNode()
            combinedNode.put("@event_name", "combined_message")
            combinedNode.put("fodselsnr", meldepliktHendelse.ident)
            combinedNode.put("timestamp", LocalDateTime.now().toString())

            // Add the original messages
            combinedNode.set<JsonNode>("meldeplikt", meldepliktNode)
            combinedNode.set<JsonNode>("meldegruppe", meldegruppeNode)

            return objectMapper.writeValueAsString(combinedNode)
        } catch (e: Exception) {
            logger.error(e) { "Failed to combine messages: meldeplikt=$meldepliktJson, meldegruppe=$meldegruppeJson" }
            throw e
        }
    }

    /**
     * Converts a JsonNode to a MeldepliktHendelse.
     */
    fun convertToMeldepliktHendelse(node: JsonNode): MeldepliktHendelse {
        val afterNode = node.path("after")
        val ident: String = afterNode.path("FODSELSNR").asText()
        val hendelseId = afterNode.path("HENDELSE_ID").asText()
        val dato =
            if (afterNode.path("HENDELSESDATO").isMissingOrNull()) {
                LocalDateTime.now()
            } else {
                afterNode
                    .path("HENDELSESDATO")
                    .asText()
                    .arenaDato()
            }
        val startDato =
            if (afterNode.path("DATO_FRA").isMissingOrNull()) {
                LocalDateTime.now()
            } else {
                afterNode.path("DATO_FRA").asText().arenaDato()
            }
        val sluttDato =
            if (afterNode.path("DATO_TIL").isMissingOrNull()) {
                null
            } else {
                afterNode.path("DATO_TIL").asText().arenaDato()
            }
        val statusMeldeplikt = afterNode.path("STATUS_MELDEPLIKT").asText() == "J"

        return MeldepliktHendelse(ident, dato, hendelseId, startDato, sluttDato, statusMeldeplikt)
    }

    /**
     * Checks if a JsonNode is missing or null.
     */
    fun JsonNode.isMissingOrNull(): Boolean = this.isMissingNode || this.isNull

    /**
     * Converts a JsonNode to a MeldegruppeHendelse (either DagpengerMeldegruppeHendelse or AnnenMeldegruppeHendelse).
     */
    fun convertToMeldegruppeHendelse(node: JsonNode): Hendelse {
        val afterNode = node.path("after")
        val ident: String = afterNode.path("FODSELSNR").asText()
        val meldegruppeKode = afterNode.path("MELDEGRUPPEKODE").asText()
        val dato =
            if (afterNode.path("HENDELSESDATO").isMissingOrNull()) {
                LocalDateTime.now()
            } else {
                afterNode
                    .path("HENDELSESDATO")
                    .asText()
                    .arenaDato()
            }
        val startDato =
            if (afterNode.path("DATO_FRA").isMissingOrNull()) {
                LocalDateTime.now()
            } else {
                afterNode.path("DATO_FRA").asText().arenaDato()
            }
        val sluttDato =
            if (afterNode.path("DATO_TIL").isMissingOrNull()) {
                null
            } else {
                afterNode.path("DATO_TIL").asText().arenaDato()
            }
        val hendelseId = afterNode.path("HENDELSE_ID").asText()
        val harMeldtSeg =
            if (afterNode.path("HAR_MELDT_SEG").isMissingOrNull()) {
                true
            } else {
                afterNode.path("HAR_MELDT_SEG").asText() == "J"
            }

        return if (meldegruppeKode == "DAGP") {
            DagpengerMeldegruppeHendelse(
                ident = ident,
                dato = dato,
                startDato = startDato,
                sluttDato = sluttDato,
                referanseId = hendelseId,
                meldegruppeKode = meldegruppeKode,
                harMeldtSeg = harMeldtSeg,
            )
        } else {
            AnnenMeldegruppeHendelse(
                ident = ident,
                dato = dato,
                startDato = startDato,
                sluttDato = sluttDato,
                referanseId = hendelseId,
                meldegruppeKode = meldegruppeKode,
                harMeldtSeg = harMeldtSeg,
            )
        }
    }

    /**
     * Helper function to parse Arena date format.
     * Converts a string in the format "yyyy-MM-dd HH:mm:ss" to a LocalDateTime.
     */
    fun String.arenaDato(): LocalDateTime {
        val pattern = "yyyy-MM-dd HH:mm:ss"
        val formatter = DateTimeFormatter.ofPattern(pattern)
        return LocalDateTime.parse(this, formatter)
    }
}
