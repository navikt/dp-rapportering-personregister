package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import mu.KotlinLogging

class MeldekortTestdataMottak(
    rapidsConnection: RapidsConnection,
) : River.PacketListener {
    init {
        logger.info { "Starter MeldekortTestdataMottak" }
        River(rapidsConnection)
            .apply {
                precondition { it.requireValue("@event_name", "meldekort_innsendt_test") }
                validate { it.requireKey("ident") }
            }.register(this)
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry,
    ) {
        logger.info { "Tar imot meldekort testdata: ${packet.toJson()}" }
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
