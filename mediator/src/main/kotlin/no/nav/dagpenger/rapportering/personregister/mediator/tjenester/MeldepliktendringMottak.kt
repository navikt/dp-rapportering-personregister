package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.isMissingOrNull
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import io.opentelemetry.instrumentation.annotations.WithSpan
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.FremtidigHendelseMediator
import no.nav.dagpenger.rapportering.personregister.mediator.PersonMediator
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.MeldepliktendringMetrikker
import no.nav.dagpenger.rapportering.personregister.modell.MeldepliktHendelse
import java.time.LocalDateTime

private val logger = KotlinLogging.logger {}

class MeldepliktendringMottak(
    rapidsConnection: RapidsConnection,
    private val personMediator: PersonMediator,
    private val fremtidigHendelseMediator: FremtidigHendelseMediator,
    private val meldepliktendringMetrikker: MeldepliktendringMetrikker,
) : River.PacketListener {
    init {
        River(rapidsConnection)
            .apply {
                validate { it.requireValue("table", "ARENA_GOLDENGATE.MELDEPLIKT") }
                validate { it.requireKey("after", "after.STATUS_AKTIV") }
                validate { it.requireKey("after.FODSELSNR", "after.HENDELSE_ID", "after.DATO_FRA", "after.STATUS_MELDEPLIKT") }
                validate { it.interestedIn("after.DATO_TIL", "after.HAR_MELDT_SEG") }
                validate { it.forbidValue("after.STATUS_AKTIV", "N") }
            }.register(this)
    }

    @WithSpan
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
                meldepliktendringMetrikker.fremtidigMeldepliktendringMottatt.increment()
                fremtidigHendelseMediator.behandle(hendelse)
            } else {
                meldepliktendringMetrikker.meldepliktendringMottatt.increment()
                personMediator.behandle(hendelse)
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
    val harMeldtSeg =
        if (this["after"]["HAR_MELDT_SEG"]?.isMissingOrNull() != false) {
            true
        } else {
            this["after"]["HAR_MELDT_SEG"].asText() == "J"
        }

    return MeldepliktHendelse(
        ident = ident,
        dato = dato,
        referanseId = hendelseId,
        startDato = startDato,
        sluttDato = sluttDato,
        statusMeldeplikt = statusMeldeplikt,
        harMeldtSeg = harMeldtSeg,
    )
}
