package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDateTime
import com.github.navikt.tbd_libs.rapids_and_rivers.isMissingOrNull
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.PersonstatusMediator
import no.nav.dagpenger.rapportering.personregister.mediator.hendelser.ArbeidssøkerHendelse
import java.util.UUID

class ArbeidssøkerperiodeMottak(
    rapidsConnection: RapidsConnection,
    private val personstatusMediator: PersonstatusMediator,
) : River.PacketListener {
    init {
        River(rapidsConnection)
            .apply {
                validate { it.requireValue("@event_name", "arbeidssøkerperiode_event") }
                validate { it.requireKey("ident", "periodeId", "startDato") }
                validate { it.interestedIn("sluttDato") }
            }.register(this)
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry,
    ) {
        logger.info { "Mottok arbeidssøkerperiode: ${packet.toJson()}" }

        try {
            personstatusMediator.behandle(packet.tilHendelse())
        } catch (e: Exception) {
            logger.error(e) { "Feil ved behandling av arbeidssøkerperiode" }
        }
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}

private fun JsonMessage.tilHendelse(): ArbeidssøkerHendelse =
    ArbeidssøkerHendelse(
        ident = this["ident"].asText(),
        periodeId = UUID.fromString(this["periodeId"].asText()),
        startDato = this["startDato"].asLocalDateTime(),
        sluttDato = if (this["sluttDato"].isMissingOrNull()) null else this["sluttDato"].asLocalDateTime(),
    )
