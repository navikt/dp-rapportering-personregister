package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import com.fasterxml.jackson.databind.JsonNode
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.isMissingOrNull
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.PersonstatusMediator
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.VedtakMetrikker
import no.nav.dagpenger.rapportering.personregister.modell.AvslagHendelse
import no.nav.dagpenger.rapportering.personregister.modell.Hendelse
import no.nav.dagpenger.rapportering.personregister.modell.InnvilgelseHendelse
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class VedtakMottak(
    rapidsConnection: RapidsConnection,
    private val personStatusMediator: PersonstatusMediator,
    private val vedtakMetrikker: VedtakMetrikker,
) : River.PacketListener {
    init {
        River(rapidsConnection)
            .apply {
                validate { it.requireValue("table", "SIAMO.VEDTAK") }
                validate {
                    it.requireKey(
                        "op_ts",
                        "after.VEDTAK_ID",
                    )
                }
                validate { it.requireAny("after.VEDTAKTYPEKODE", listOf("O", "G")) }
                validate { it.requireAny("after.UTFALLKODE", listOf("JA", "NEI")) }
                validate { it.interestedIn("after", "tokens") }
                validate { it.interestedIn("tokens.FODSELSNR") }
                validate { it.interestedIn("FODSELSNR") }
            }.register(this)
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry,
    ) {
        logger.info { "Mottok nytt vedtak" }

        try {
            when (val hendelse = packet.tilHendelse()) {
                is InnvilgelseHendelse -> {
                    logger.info { "Mottok innvilgelse vedtak for person ${hendelse.ident}" }
                    personStatusMediator.behandle(hendelse)
                }

                is AvslagHendelse -> {
                    logger.info { "Mottok avslag vedtak for person ${hendelse.ident}" }
                    personStatusMediator.behandle(hendelse)
                }

                else -> {
                    logger.error { "Ukjent hendelse $hendelse" }
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Feil ved behandling av vedtak $e" }
        }
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}

private fun JsonMessage.tilHendelse(): Hendelse {
    val ident: String = this.fødselsnummer()
    val referanseId = this["after"]["VEDTAK_ID"].asText()
    val dato = this["op_ts"].asArenaDato()

    return when (val status = this["after"]["UTFALLKODE"].asText()) {
        "JA" -> InnvilgelseHendelse(ident, dato, referanseId)
        "NEI" -> AvslagHendelse(ident, dato, referanseId)
        else -> throw IllegalArgumentException("Ukjent utfallkode $status")
    }
}

private fun JsonNode.asArenaDato() =
    asText().let {
        LocalDateTime.parse(
            it,
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss[.SSSSSS]"),
        )
    }

internal fun JsonMessage.fødselsnummer(): String =
    if (this["tokens"].isMissingOrNull()) this["FODSELSNR"].asText() else this["tokens"]["FODSELSNR"].asText()
