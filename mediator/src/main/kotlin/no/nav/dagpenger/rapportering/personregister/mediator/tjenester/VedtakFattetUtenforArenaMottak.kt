package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.core.instrument.MeterRegistry
import no.nav.dagpenger.rapportering.personregister.mediator.db.MeldingerRepository
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.VedtakMetrikker
import no.nav.dagpenger.rapportering.personregister.mediator.service.BehandlingService
import no.nav.dagpenger.rapportering.personregister.mediator.utils.UUIDv7
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.VedtakFattetUtenforArenaHendelse

private val logger = KotlinLogging.logger {}
private val sikkerLogg = KotlinLogging.logger("tjenestekall")

class VedtakFattetUtenforArenaMottak(
    rapidsConnection: RapidsConnection,
    private val behandlingService: BehandlingService,
    private val meldingerRepository: MeldingerRepository,
    private val vedtakMetrikker: VedtakMetrikker,
) : River.PacketListener {
    init {
        River(rapidsConnection)
            .apply {
                precondition { it.requireValue("@event_name", "vedtak_fattet_utenfor_arena") }
                validate { it.requireKey("@id", "behandlingId", "søknadId", "ident", "sakId") }
                validate { it.requireValue("førteTil", "Innvilgelse") }
            }.register(this)
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry,
    ) {
        val ident = packet["ident"].asString()

        logger.info { "Mottok vedtak_fattet_utenfor_arena-melding" }
        sikkerLogg.info { "Mottok vedtak_fattet_utenfor_arena-melding, ident=$ident: ${packet.toJson()}" }
        vedtakMetrikker.vedtakFattetUtenforArenaMottatt.increment()

        try {
            val korrelasjonsId = UUIDv7.fromString(packet["@id"].asString())
            val behandlingId = packet["behandlingId"].asString()
            val søknadId = packet["søknadId"].asString()
            val sakId = packet["sakId"].asString()

            val relevantMeldingsinnhold =
                """
                {
                    "@event_name": "${packet["@event_name"].asString()}",
                    "behandlingId": "$behandlingId",
                    "søknadId": "$søknadId",
                    "sakId": "$sakId"
                }
                """.trimIndent()

            meldingerRepository.lagreInnkommendeMelding(
                korrelasjonsId = korrelasjonsId,
                ident = ident,
                relevantMeldingsinnhold = relevantMeldingsinnhold,
            )

            behandlingService.behandle(
                VedtakFattetUtenforArenaHendelse(
                    korrelasjonsId = korrelasjonsId,
                    ident = ident,
                    referanseId = packet["@id"].asString(),
                    behandlingId = behandlingId,
                    søknadId = søknadId,
                    sakId = sakId,
                ),
            )
        } catch (e: Exception) {
            logger.error(e) { "Feil ved behandling av vedtak_fattet_utenfor_arena-melding" }
            sikkerLogg.error(e) { "Feil ved behandling av vedtak_fattet_utenfor_arena-melding, ident=$ident: ${packet.toJson()}" }
            vedtakMetrikker.vedtakFattetUtenforArenaFeilet.increment()
            throw e
        }
    }
}
