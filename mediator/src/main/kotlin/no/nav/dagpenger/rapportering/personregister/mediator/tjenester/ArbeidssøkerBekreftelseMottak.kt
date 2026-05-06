package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.rapportering.personregister.mediator.service.ArbeidssøkerBekreftelseService

private val logger = KotlinLogging.logger {}
private val sikkerlogg = KotlinLogging.logger("tjenestekall")

class ArbeidssøkerBekreftelseMottak(
    rapidsConnection: RapidsConnection,
    private val arbeidssøkerBekreftelseService: ArbeidssøkerBekreftelseService,
) : River.PacketListener {
    init {
        River(rapidsConnection)
            .apply {
                precondition {
                    it.requireValue(
                        "@event_name",
                        "arbeidssøkerbekreftelse",
                    )
                }
                validate {
                    it.requireKey(
                        "ident",
                        "bekreftelse",
                    )
                }
            }.register(this)
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry,
    ) {
        try {
            logger.info { "Mottok arbeidssøkerbekreftelse melding" }
            sikkerlogg.info { "Mottok arbeidssøkerbekreftelse melding: ${packet.toJson()}" }

            val melding = packet.tilArbeidssøkerBekreftelseMelding()
            runBlocking { arbeidssøkerBekreftelseService.behandle(melding) }
        } catch (e: Exception) {
            logger.error(e) { "Feil ved behandling av arbeidssøkerbekreftelse" }
            throw e
        }
    }
}
