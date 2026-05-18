package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.core.instrument.MeterRegistry
import io.opentelemetry.instrumentation.annotations.WithSpan
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.rapportering.personregister.mediator.connector.tilArbeidssøkerBekreftelseMelding
import no.nav.dagpenger.rapportering.personregister.mediator.service.ArbeidssøkerBekreftelseService

private val logger = KotlinLogging.logger {}
private val sikkerlogg = KotlinLogging.logger("tjenestekall")

class ArbeidssøkerBekreftelseMottak(
    rapidsConnection: RapidsConnection,
    private val arbeidssøkerBekreftelseService: ArbeidssøkerBekreftelseService,
) : River.PacketListener {
    val meldingerSomSkalIgnoreres = listOf("bae64281-c3f3-493b-bc57-3424a3f6c2e5", "019e1c47-0dfa-73d7-90d3-3302854ee0e1")

    init {
        logger.info { "Starter ArbeidssøkerBekreftelseMottak" }
        River(rapidsConnection)
            .apply {
                precondition {
                    it.requireValue("@event_name", "arbeidssøkerbekreftelse")
                }
                validate {
                    it.requireKey(
                        "ident",
                        "bekreftelse",
                    )
                }
                validate { it.interestedIn("@id") }
            }.register(this)
    }

    @WithSpan
    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry,
    ) {
        logger.info { "Mottok arbeidssøkerbekreftelse" }
        sikkerlogg.info { "Mottok arbeidssøkerbekreftelse ${packet.toJson()}" }

        if (meldingerSomSkalIgnoreres.contains(packet["@id"].asText())) {
            logger.info { "Melding med id ${packet["@id"].asText()} er i listen over meldinger som skal ignoreres, så ignorer den." }
            return
        }

        val arbeidssøkerBekreftelseMelding =
            try {
                packet.tilArbeidssøkerBekreftelseMelding()
            } catch (e: Exception) {
                logger.error(e) { "Feil ved parsing av arbeidssøkerbekreftelse melding" }
                sikkerlogg.error(e) { "Feil ved parsing av arbeidssøkerbekreftelse melding: ${packet.toJson()}" }
                throw e
            }

        try {
            logger.info {
                "Mottok arbeidssøkerbekreftelse melding for periode: ${arbeidssøkerBekreftelseMelding.bekreftelse.periodeId}"
            }
            runBlocking { arbeidssøkerBekreftelseService.behandle(arbeidssøkerBekreftelseMelding) }
        } catch (e: Exception) {
            logger.error(e) {
                "Feil ved behandling av arbeidssøkerbekreftelse for periode: ${arbeidssøkerBekreftelseMelding.bekreftelse.periodeId}"
            }
            sikkerlogg.error(e) {
                "Feil ved behandling av arbeidssøkerbekreftelse for periode: ${arbeidssøkerBekreftelseMelding.bekreftelse.periodeId}. Melding: $arbeidssøkerBekreftelseMelding"
            }
            throw e
        }
    }
}
