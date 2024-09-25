package no.nav.dagpenger.rapportering.personregister.mediator.melding

import com.fasterxml.jackson.databind.JsonNode
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.isMissingOrNull
import no.nav.dagpenger.rapportering.personregister.mediator.hendelser.VedtakHendelse
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class Vedtaksmelding(
    private val packet: JsonMessage,
) {
    companion object {
        private var formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss[.SSSSSS]")

        private fun JsonNode.asArenaDato() = asText().let { LocalDateTime.parse(it, formatter) }

        private fun JsonNode.asOptionalArenaDato() =
            takeIf(JsonNode::isTextual)
                ?.asText()
                ?.takeIf(String::isNotEmpty)
                ?.let { LocalDateTime.parse(it, formatter) }
    }

    private val vedtakId = packet["after"]["VEDTAK_ID"].asText()
    private val sakId = packet["after"]["SAK_ID"].asText()

    private val fattet = packet["op_ts"].asArenaDato()
    private val fraDato = packet["after"]["FRA_DATO"].asArenaDato()
    private val tilDato = packet["after"]["TIL_DATO"].asOptionalArenaDato()
    private val fnr: String = packet.fødselsnummer()

    private val status
        get() =
            when (packet["after"]["UTFALLKODE"].asText()) {
                "JA" -> Vedtakstype.INNVILGET
                "NEI" -> Vedtakstype.AVSLÅTT
                else -> throw IllegalArgumentException("Ukjent utfallskode")
            }

    fun tilVedtakHendelse() =
        VedtakHendelse(
            fnr = fnr,
            vedtakId = vedtakId,
            sakId = sakId,
            fattet = fattet,
            fraDato = fraDato,
            tilDato = tilDato,
            status = status,
        )
}

enum class Vedtakstype {
    INNVILGET,
    AVSLÅTT,
    STANS,
    ENDRING,
}

internal fun JsonMessage.fødselsnummer(): String =
    if (this["tokens"].isMissingOrNull()) this["FODSELSNR"].asText() else this["tokens"]["FODSELSNR"].asText()
