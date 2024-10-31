package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDate
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.PersonstatusMediator
import no.nav.dagpenger.rapportering.personregister.modell.RegistrertSomArbeidssøkerLøsning

class ArbeidssøkerMottak(
    rapidsConnection: RapidsConnection,
    private val personStatusMediator: PersonstatusMediator,
) : River.PacketListener {
    init {
        River(rapidsConnection)
            .apply {
                validate { it.demandValue("@event_name", "behov") }
                validate { it.requireKey("@løsning") }
                validate { it.requireAll("@behov", listOf("RegistrertSomArbeidssøker")) }
                validate { it.requireKey("ident") }
            }.register(this)
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
    ) {
        logger.info { "Mottok løsning på behov om arbeidssøker" }
        sikkerlogg.info { "Mottok løsning på behov om arbeidssøker. Packet: ${packet.toJson()}" }

        try {
            personStatusMediator.behandle(packet.tilRegistrertSomArbeidssøkerLøsning())
        } catch (e: Exception) {
            logger.error(e) { "Feil ved behandling av arbeidssøkerstatus $e" }
        }
    }

    companion object {
        private val logger = KotlinLogging.logger {}
        val sikkerlogg = KotlinLogging.logger("tjenestekall")
    }
}

private fun JsonMessage.tilRegistrertSomArbeidssøkerLøsning(): RegistrertSomArbeidssøkerLøsning {
    val løsning = this["@løsning"]["RegistrertSomArbeidssøker"]
    return RegistrertSomArbeidssøkerLøsning(
        ident = this["ident"].asText(),
        verdi = løsning["verdi"].asBoolean(),
        gyldigFraOgMed = løsning["gyldigFraOgMed"].asLocalDate(),
        gyldigTilOgMed = løsning["gyldigTilOgMed"].asLocalDate(),
    )
}
