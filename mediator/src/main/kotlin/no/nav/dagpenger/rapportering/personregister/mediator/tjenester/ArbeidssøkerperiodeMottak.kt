package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.isMissingOrNull
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.ArbeidssøkerMediator
import no.nav.dagpenger.rapportering.personregister.modell.ArbeidssøkerperiodeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.AvsluttetArbeidssøkerperiodeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.StartetArbeidssøkerperiodeHendelse
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

class ArbeidssøkerperiodeMottak(
    rapidsConnection: RapidsConnection,
    private val arbeidssøkerMediator: ArbeidssøkerMediator,
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
            arbeidssøkerMediator.behandle(packet.tilHendelse())
        } catch (e: Exception) {
            logger.error(e) { "Feil ved behandling av arbeidssøkerperiode" }
        }
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}

private fun JsonMessage.tilHendelse(): ArbeidssøkerperiodeHendelse {
    val ident = this["identitetsnummer"].asText()
    val periodeId = UUID.fromString(this["id"].asText())
    val startDato = this["startet"]["tidspunkt"].asLong().asLocalDateTime()
    val sluttDato = if (this["avsluttet"].isMissingOrNull()) null else this["avsluttet"]["tidspunkt"].asLong().asLocalDateTime()

    if (sluttDato == null) {
        return StartetArbeidssøkerperiodeHendelse(
            periodeId = periodeId,
            ident = ident,
            startet = startDato,
        )
    }

    return AvsluttetArbeidssøkerperiodeHendelse(
        periodeId = periodeId,
        ident = ident,
        startet = startDato,
        avsluttet = sluttDato,
    )
}

private fun Long.asLocalDateTime() =
    LocalDateTime.ofInstant(
        Instant.ofEpochMilli(this),
        ZoneId.systemDefault(),
    )
