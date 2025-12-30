package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.core.instrument.MeterRegistry
import io.opentelemetry.instrumentation.annotations.WithSpan
import no.nav.dagpenger.rapportering.personregister.mediator.PersonMediator
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.NødbremsHendelse
import java.time.LocalDateTime
import java.util.UUID

private val logger = KotlinLogging.logger {}
private val sikkerLogg = KotlinLogging.logger("tjenestekall")

class NødbremsMottak(
    val rapidsConnection: RapidsConnection,
    private val personMediator: PersonMediator,
) : River.PacketListener {
    init {
        River(rapidsConnection)
            .apply {
                precondition { it.requireValue("@event_name", "ramps_nødbrems") }
                validate {
                    it.requireKey("ident")
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
        val ident = packet["ident"].asText()

        logger.info { "Mottok melding om at nødbrems aktiveres" }
        sikkerLogg.info { "Mottok melding om at nødbrems aktiveres for $ident" }

        try {
            personMediator.behandle(
                NødbremsHendelse(
                    ident = ident,
                    dato = LocalDateTime.now(),
                    startDato = LocalDateTime.now(),
                    referanseId = UUID.randomUUID().toString(),
                ),
            )
        } catch (e: Exception) {
            logger.error(e) { "Feil ved behandling av nødbrems" }
            sikkerLogg.error(e) { "Feil ved behandling av nødbrems: ${packet.toJson()}" }
            throw e
        }
    }
}
