package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.isMissingOrNull
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.PersonstatusMediator
import no.nav.dagpenger.rapportering.personregister.modell.ArbeidssøkerHendelse
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

class ArbeidssøkerperiodeMottak(
    rapidsConnection: RapidsConnection,
    private val personstatusMediator: PersonstatusMediator,
) : River.PacketListener {
    init {
        River(rapidsConnection)
            .apply {
                validate { it.requireKey("id", "identitetsnummer", "startet") }
                validate { it.interestedIn("avsluttet") }
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
        ident = this["identitetsnummer"].asText(),
        periodeId = UUID.fromString(this["id"].asText()),
        startDato = this["startet"]["tidspunkt"].asLong().asLocalDateTime(),
        sluttDato = if (this["avsluttet"].isMissingOrNull()) null else this["avsluttet"]["tidspunkt"].asLong().asLocalDateTime(),
    )

private fun Long.asLocalDateTime() =
    LocalDateTime.ofInstant(
        Instant.ofEpochMilli(this),
        ZoneId.systemDefault(),
    )
