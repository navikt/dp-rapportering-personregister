package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.isMissingOrNull
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.FremtidigHendelseMediator
import no.nav.dagpenger.rapportering.personregister.mediator.PersonstatusMediator
import no.nav.dagpenger.rapportering.personregister.modell.MeldepliktHendelse
import java.time.LocalDateTime

val logger = KotlinLogging.logger {}

class MeldepliktendringMottak(
    rapidsConnection: RapidsConnection,
    private val personstatusMediator: PersonstatusMediator,
    private val fremtidigHendelseMediator: FremtidigHendelseMediator,
) : River.PacketListener {
    init {
        River(rapidsConnection)
            .apply {
                validate { it.requireValue("table", "ARENA_GOLDENGATE.MELDEPLIKT") }
                validate { it.requireKey("after") }
                validate { it.requireKey("after.FODSELSNR", "after.HENDELSE_ID", "after.DATO_FRA", "after.STATUS_MELDEPLIKT") }
                validate { it.interestedIn("after.DATO_TIL") }
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
            val hendelse = packet.tilHendelse()
            if (hendelse.startDato.isAfter(LocalDateTime.now())) {
                fremtidigHendelseMediator.behandle(hendelse)
            } else {
                personstatusMediator.behandle(hendelse)
            }
        } catch (e: Exception) {
            logger.error(e) { "Feil ved behandling av meldepliktendring $e" }
        }
    }
}

private fun JsonMessage.tilHendelse(): MeldepliktHendelse {
    val ident: String = this["after"]["FODSELSNR"].asText()
    val hendelseId = this["after"]["HENDELSE_ID"].asText()
    val dato =
        if (this["after"]["HENDELSESDATO"].isMissingOrNull()) {
            LocalDateTime.now()
        } else {
            this["after"]["HENDELSESDATO"]
                .asText()
                .arenaDato()
        }
    val startDato = this["after"]["DATO_FRA"].asText().arenaDato()
    val sluttDato = if (this["after"]["DATO_TIL"].isMissingOrNull()) null else this["after"]["DATO_TIL"].asText().arenaDato()
    val statusMeldeplikt = this["after"]["STATUS_MELDEPLIKT"].asText().let { it == "J" }

    return MeldepliktHendelse(ident, dato, startDato, sluttDato, statusMeldeplikt, hendelseId)
}
