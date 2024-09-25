package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDateTime
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.PersonstatusMediator
import no.nav.dagpenger.rapportering.personregister.mediator.hendelser.SøknadHendelse

class SøknadMottak(
    rapidsConnection: RapidsConnection,
    private val personStatusMediator: PersonstatusMediator,
) : River.PacketListener {
    init {
        River(rapidsConnection)
            .apply {
                validate { it.demandValue("@event_name", "søknad_innsendt_varsel") }
                validate { it.requireKey("ident", "søknadId", "@opprettet", "søknadstidspunkt") }
                validate { it.interestedIn("@id", "@opprettet") }
            }.register(this)
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
    ) {
        logger.info { "Mottok søknad innsendt hendelse for søknad ${packet["søknadId"]}" }

        personStatusMediator.behandle(packet.tilHendelse())
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}

private fun JsonMessage.tilHendelse(): SøknadHendelse {
    val ident = this["ident"].asText()
    val referanseId = this["søknadId"].asText()
    val dato = this["@opprettet"].asLocalDateTime()
    return SøknadHendelse(ident, referanseId, dato)
}
