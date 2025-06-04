package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import io.opentelemetry.instrumentation.annotations.WithSpan
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.PersonMediator
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.SoknadMetrikker
import no.nav.dagpenger.rapportering.personregister.modell.SøknadHendelse
import java.time.OffsetDateTime

class SøknadMottak(
    rapidsConnection: RapidsConnection,
    private val personStatusMediator: PersonMediator,
    private val soknadMetrikker: SoknadMetrikker,
) : River.PacketListener {
    init {
        River(rapidsConnection)
            .apply {
                validate { it.requireValue("@event_name", "søknad_innsendt_varsel") }
                validate { it.requireKey("@id", "ident", "søknadId", "søknadstidspunkt") }
            }.register(this)
    }

    @WithSpan
    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry,
    ) {
        logger.info { "Mottok søknad innsendt hendelse for søknad ${packet["søknadId"]}" }
        soknadMetrikker.soknaderMottatt.increment()

        try {
            personStatusMediator.behandle(packet.tilHendelse())
        } catch (e: Exception) {
            logger.error(e) { "Feil ved behandling av søknad $e" }
        }
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}

private fun JsonMessage.tilHendelse(): SøknadHendelse {
    val ident = this["ident"].asText()
    val referanseId = this["søknadId"].asText()
    val datoString = this["søknadstidspunkt"].asText()
    val dato = OffsetDateTime.parse(datoString).toLocalDateTime()

    return SøknadHendelse(ident = ident, dato = dato, startDato = dato, referanseId = referanseId)
}
