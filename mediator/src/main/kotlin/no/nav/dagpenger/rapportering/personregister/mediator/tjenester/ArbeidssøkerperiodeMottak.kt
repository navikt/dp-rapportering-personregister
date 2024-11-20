package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.River.PacketListener
import com.github.navikt.tbd_libs.rapids_and_rivers.isMissingOrNull
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.PersonstatusMediator
import no.nav.dagpenger.rapportering.personregister.mediator.hendelser.ArbeidssøkerHendelse
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

class ArbeidssøkerperiodeMottak(
    rapidsConnection: RapidsConnection,
    private val personstatusMediator: PersonstatusMediator,
) : PacketListener {
    init {
        River(rapidsConnection)
            .apply {
                validate { it.requireKey("id", "identitetsnummer", "startet") }
                validate { it.interestedIn("avsluttet") }
            }
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
    ) {
        logger.info { "Mottok melding om endring i arbeidssøkerperiode" }

        try {
            personstatusMediator
                .behandle(
                    packet
                        .tilHendelse(),
                    // .also { TODO("Skal vi ha med metrikker her?") },
                )
        } catch (e: Exception) {
            logger.error(e) { "Feil ved behandling av arbeidssøkerperiode $e" }
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
        startDato = this["startet"]["tidspunkt"].asLong().convertMillisToLocalDateTime(),
        sluttDato =
            if (this["avsluttet"].isMissingOrNull()) {
                null
            } else {
                this["avsluttet"].let { it["tidspunkt"].asLong().convertMillisToLocalDateTime() }
            },
    )

fun Long.convertMillisToLocalDateTime(): LocalDateTime =
    LocalDateTime
        .ofInstant(Instant.ofEpochMilli(this), ZoneId.systemDefault())
