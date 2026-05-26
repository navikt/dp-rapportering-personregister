package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.core.instrument.MeterRegistry
import io.opentelemetry.instrumentation.annotations.WithSpan
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.rapportering.personregister.mediator.connector.tilArbeidssøkerBekreftelseMelding
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.ArbeidssøkerBekreftelseFraDpMeldekortregisterMetrikker
import no.nav.dagpenger.rapportering.personregister.mediator.service.ArbeidssøkerBekreftelseService

private val logger = KotlinLogging.logger {}
private val sikkerlogg = KotlinLogging.logger("tjenestekall")

class ArbeidssøkerBekreftelseMottak(
    rapidsConnection: RapidsConnection,
    private val arbeidssøkerBekreftelseService: ArbeidssøkerBekreftelseService,
    private val arbeidssøkerBekreftelseFraDpMeldekortregisterMetrikker: ArbeidssøkerBekreftelseFraDpMeldekortregisterMetrikker,
) : River.PacketListener {
    init {
        logger.info { "Starter ArbeidssøkerBekreftelseMottak" }
        River(rapidsConnection)
            .apply {
                precondition {
                    it.requireValue("@event_name", "arbeidssøkerbekreftelse")
                }
                validate {
                    it.requireKey(
                        "ident",
                        "bekreftelse",
                    )
                }
                validate { it.interestedIn("@id") }
            }.register(this)
    }

    @WithSpan
    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry,
    ) {
        logger.info { "Mottok arbeidssøkerbekreftelse" }
        sikkerlogg.info { "Mottok arbeidssøkerbekreftelse ${packet.toJson()}" }

        arbeidssøkerBekreftelseFraDpMeldekortregisterMetrikker.arbeidssøkerbekreftelseMottatt.increment()

        val arbeidssøkerBekreftelseMelding =
            try {
                packet.tilArbeidssøkerBekreftelseMelding()
            } catch (e: Exception) {
                logger.error(e) { "Feil ved parsing av arbeidssøkerbekreftelse melding" }
                sikkerlogg.error(e) { "Feil ved parsing av arbeidssøkerbekreftelse melding: ${packet.toJson()}" }
                throw e
            }

        try {
            logger.info {
                "Mottok arbeidssøkerbekreftelse melding for periode: ${arbeidssøkerBekreftelseMelding.bekreftelse.periodeId}"
            }
            runBlocking { arbeidssøkerBekreftelseService.behandle(arbeidssøkerBekreftelseMelding) }
        } catch (e: Exception) {
            arbeidssøkerBekreftelseFraDpMeldekortregisterMetrikker.arbeidssøkerbekreftelseMottakFeilet.increment()
            logger.error(e) {
                "Feil ved behandling av arbeidssøkerbekreftelse for periode: ${arbeidssøkerBekreftelseMelding.bekreftelse.periodeId}"
            }
            sikkerlogg.error(e) {
                "Feil ved behandling av arbeidssøkerbekreftelse for periode: ${arbeidssøkerBekreftelseMelding.bekreftelse.periodeId}. Melding: $arbeidssøkerBekreftelseMelding"
            }
            throw e
        }
    }
}
