package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.core.instrument.MeterRegistry
import no.nav.dagpenger.rapportering.personregister.mediator.db.BehandlingRepository
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.VedtakMetrikker

private val logger = KotlinLogging.logger {}
private val sikkerLogg = KotlinLogging.logger("tjenestekall")

class VedtakFattetUtenforArenaMottak(
    rapidsConnection: RapidsConnection,
    private val behandlingRepository: BehandlingRepository,
    private val vedtakMetrikker: VedtakMetrikker,
) : River.PacketListener {
    init {
        River(rapidsConnection)
            .apply {
                precondition { it.requireValue("@event_name", "vedtak_fattet_utenfor_arena") }
                validate { it.requireKey("behandlingId", "søknadId", "ident", "sakId") }
            }.register(this)
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry,
    ) {
        val ident = packet["ident"].asText()

        logger.info { "Mottok vedtak_fattet_utenfor_arena-melding" }
        sikkerLogg.info { "Mottok vedtak_fattet_utenfor_arena-melding, ident=$ident: ${packet.toJson()}" }
        vedtakMetrikker.vedtakFattetUtenforArenaMottatt.increment()

        try {
            val behandlingId = packet["behandlingId"].asText()
            val søknadId = packet["søknadId"].asText()
            val sakId = packet["sakId"].asText()

            behandlingRepository.lagreData(behandlingId, søknadId, ident, sakId)
        } catch (e: Exception) {
            logger.error(e) { "Feil ved behandling av vedtak_fattet_utenfor_arena-melding" }
            sikkerLogg.error(e) { "Feil ved behandling av vedtak_fattet_utenfor_arena-melding, ident=$ident: ${packet.toJson()}" }
            vedtakMetrikker.vedtakFattetUtenforArenaFeilet.increment()
            throw e
        }
    }
}
