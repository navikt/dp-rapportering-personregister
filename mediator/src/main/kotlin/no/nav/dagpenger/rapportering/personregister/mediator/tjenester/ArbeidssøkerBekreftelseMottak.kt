package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import no.nav.dagpenger.rapportering.personregister.mediator.service.ArbeidssøkerBekreftelseService

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
        val ident = packet["ident"].asText()
        arbeidssøkerBekreftelseService.behandle(ident)
    }
}
