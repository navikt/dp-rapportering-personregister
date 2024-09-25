package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.PersonstatusMediator
import no.nav.dagpenger.rapportering.personregister.mediator.melding.Vedtaksmelding

class VedtakMottak(
    rapidsConnection: RapidsConnection,
    private val personStatusMediator: PersonstatusMediator,
) : River.PacketListener {
    init {
        River(rapidsConnection)
            .apply {
                validate { it.demandValue("table", "SIAMO.VEDTAK") }
                validate {
                    it.requireKey(
                        "op_ts",
                        "after.VEDTAK_ID",
                        "after.SAK_ID",
                        "after.FRA_DATO",
                    )
                }
                validate { it.requireAny("after.VEDTAKTYPEKODE", listOf("O", "G")) }
                validate { it.requireAny("after.UTFALLKODE", listOf("JA", "NEI")) }
                validate { it.interestedIn("after", "tokens") }
                validate { it.interestedIn("after.TIL_DATO") }
                validate { it.interestedIn("tokens.FODSELSNR") }
                validate { it.interestedIn("FODSELSNR") }
            }.register(this)
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
    ) {
        logger.info { "Mottok nytt vedtak" }

        personStatusMediator.behandle(Vedtaksmelding(packet).tilVedtakHendelse())
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
