package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.MeldepliktendringMediator
import no.nav.dagpenger.rapportering.personregister.mediator.hendelser.MeldepliktendringHendelse
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.MeldepliktendringMetrikker

class MeldepliktendringMottak(
    rapidsConnection: RapidsConnection,
    private val meldepliktendringMediator: MeldepliktendringMediator,
    private val meldepliktendringMetrikker: MeldepliktendringMetrikker,
) : River.PacketListener {
    init {
        River(rapidsConnection)
            .apply {
                validate { it.requireValue("table", "ARENA_GOLDENGATE.MELDEPLIKT") }
            }.register(this)
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry,
    ) {
        logger.info { "Mottok ny meldepliktendring" }

        try {
            meldepliktendringMediator
                .behandle(
                    packet
                        .tilHendelse()
                        .also { meldepliktendringMetrikker.meldepliktendringMottatt().increment() },
                )
        } catch (e: Exception) {
            logger.error(e) { "Feil ved behandling av meldepliktendring $e" }
        }
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}

private fun JsonMessage.tilHendelse(): MeldepliktendringHendelse {
    val ident: String = this["FODSELSNR"].asText()

    return MeldepliktendringHendelse(ident)
}
