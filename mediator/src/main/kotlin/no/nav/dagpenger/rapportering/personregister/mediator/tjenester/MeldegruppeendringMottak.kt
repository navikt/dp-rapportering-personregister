package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.MeldegruppeendringMediator
import no.nav.dagpenger.rapportering.personregister.mediator.hendelser.MeldegruppeendringHendelse
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.MeldegruppeendringMetrikker

class MeldegruppeendringMottak(
    rapidsConnection: RapidsConnection,
    private val meldegruppeendringMediator: MeldegruppeendringMediator,
    private val meldegruppeendringMetrikker: MeldegruppeendringMetrikker,
) : River.PacketListener {
    init {
        River(rapidsConnection)
            .apply {
                validate { it.requireValue("table", "ARENA_GOLDENGATE.MELDEGRUPPE") }
            }.register(this)
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry,
    ) {
        logger.info { "Mottok ny meldegruppeendring" }

        try {
            meldegruppeendringMediator
                .behandle(
                    packet
                        .tilHendelse()
                        .also { meldegruppeendringMetrikker.meldegruppeendringMottatt().increment() },
                )
        } catch (e: Exception) {
            logger.error(e) { "Feil ved behandling av meldegruppeendring $e" }
        }
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}

private fun JsonMessage.tilHendelse(): MeldegruppeendringHendelse {
    val ident: String = this["FODSELSNR"].asText()

    return MeldegruppeendringHendelse(ident)
}
