package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.core.instrument.MeterRegistry
import io.opentelemetry.instrumentation.annotations.WithSpan
import no.nav.dagpenger.rapportering.personregister.mediator.MeldestatusMediator
import no.nav.dagpenger.rapportering.personregister.modell.meldestatus.MeldestatusHendelse

private val logger = KotlinLogging.logger {}

class MeldestatusMottak(
    rapidsConnection: RapidsConnection,
    private val meldestatusMediator: MeldestatusMediator,
) : River.PacketListener {
    init {
        River(rapidsConnection)
            .apply {
                validate { it.requireValue("table", "ARENA_GOLDENGATE.MELDESTATUS") }
                validate { it.requireKey("after") }
                validate {
                    it.requireKey(
                        "after.PERSON_ID",
                        "after.MELDESTATUS_ID",
                        "after.HENDELSE_ID",
                    )
                }
            }.register(this)
    }

    @WithSpan
    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry,
    ) {
        logger.info { "Mottok ny meldestatusendring" }

        try {
            val hendelse = packet.tilHendelse()
            meldestatusMediator.behandle(hendelse)
        } catch (e: Exception) {
            logger.error(e) { "Feil ved behandling av meldestatusendring" }
            throw e
        }
    }
}

private fun JsonMessage.tilHendelse(): MeldestatusHendelse =
    MeldestatusHendelse(
        personId = this["after"]["PERSON_ID"].asText().toLong(),
        meldestatusId = this["after"]["MELDESTATUS_ID"].asText().toLong(),
        hendelseId = this["after"]["HENDELSE_ID"].asText().toLong(),
    )
