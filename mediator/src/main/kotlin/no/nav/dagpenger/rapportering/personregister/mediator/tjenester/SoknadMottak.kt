package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.PersonstatusMediator
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.asLocalDateTime

class SoknadMottak(
    rapidsConnection: RapidsConnection,
    private val personStatusMediator: PersonstatusMediator,
) : River.PacketListener {
    init {
        River(rapidsConnection)
            .apply {
                validate { it.demandValue("@event_name", "søknad_innsendt_varsel") }
                validate { it.requireKey("ident", "søknadId", "søknadstidspunkt") }
                validate { it.interestedIn("@id", "@opprettet") }
            }.register(this)
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
    ) {
        logger.info { "Mottok søknad innsendt hendelse for søknad ${packet["søknadId"]}" }

        val ident = packet["ident"].asText()
        val søknadstidspunkt = packet["søknadstidspunkt"].asLocalDateTime().toLocalDate()
        val søknadId = packet["søknadId"].asText()

        personStatusMediator.behandle(ident, søknadstidspunkt, søknadId)
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
