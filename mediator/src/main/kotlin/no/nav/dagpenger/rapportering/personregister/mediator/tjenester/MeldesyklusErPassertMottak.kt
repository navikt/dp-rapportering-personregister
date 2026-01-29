package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.core.instrument.MeterRegistry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.instrumentation.annotations.WithSpan
import no.nav.dagpenger.rapportering.personregister.mediator.PersonMediator
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.MeldesyklusErPassertHendelse
import java.time.LocalDateTime

class MeldesyklusErPassertMottak(
    rapidsConnection: RapidsConnection,
    private val personMediator: PersonMediator,
) : River.PacketListener {
    companion object {
        private val logger = KotlinLogging.logger {}
        private val sikkerlogg = KotlinLogging.logger("tjenestekall")
    }

    init {
        River(rapidsConnection)
            .apply {
                precondition {
                    it.requireValue("@event_name", "meldesyklus_er_passert")
                }
                validate {
                    it.requireKey(
                        "ident",
                        "dato",
                        "referanseId",
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
        logger.info { "Mottok meldesyklus_er_passert melding" }
        sikkerlogg.info { "Mottok meldesyklus_er_passert melding: ${packet.toJson()}" }

        try {
            val ident = packet["ident"].asText()
            val referanseId = packet["referanseId"].asText()

            if (!ident.matches(Regex("[0-9]{11}"))) {
                throw IllegalArgumentException("Person-ident må ha 11 sifre")
            }

            Span.current().spanContext.traceId

            val meldesyklusErPassertHendelse =
                MeldesyklusErPassertHendelse(
                    ident,
                    LocalDateTime.now(),
                    LocalDateTime.now(),
                    referanseId,
                )

            personMediator.behandle(meldesyklusErPassertHendelse)
        } catch (e: Exception) {
            logger.error(e) { "Feil ved behandling av hendelse om at meldesyklus er passert" }
            sikkerlogg.error(e) { "Feil ved behandling av hendelse om at meldesyklus er passert: ${packet.toJson()}" }
            throw e
        }
    }
}
