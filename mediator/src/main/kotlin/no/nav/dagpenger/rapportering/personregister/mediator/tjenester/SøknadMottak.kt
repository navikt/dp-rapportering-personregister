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
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.SøknadMetrikker
import no.nav.dagpenger.rapportering.personregister.mediator.utils.UUIDv7
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.SøknadHendelse
import java.time.LocalDateTime

class SøknadMottak(
    rapidsConnection: RapidsConnection,
    private val personMediator: PersonMediator,
    private val søknadMetrikker: SøknadMetrikker,
) : River.PacketListener {
    private val quizSøknadIdNøkkel = "søknadsData.søknad_uuid"
    private val legacySøknadIdNøkkel = "søknadsData.brukerBehandlingId"

    init {
        River(rapidsConnection)
            .apply {
                precondition { it.requireValue("@event_name", "innsending_ferdigstilt") }
                validate {
                    it.requireKey(
                        "fødselsnummer",
                        "datoRegistrert",
                    )
                    it.requireAny("type", listOf("NySøknad", "Gjenopptak"))
                    it.interestedIn(
                        quizSøknadIdNøkkel,
                        legacySøknadIdNøkkel,
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
        logger.info { "Mottok innsending_ferdigstilt melding" }
        sikkerlogg.info { "Mottok innsending_ferdigstilt melding: ${packet.toJson()}" }
        søknadMetrikker.søknaderMottatt.increment()

        try {
            personMediator.behandle(packet.tilHendelse())
        } catch (e: Exception) {
            logger.error(e) { "Feil ved behandling av søknad" }
            sikkerlogg.error(e) { "Feil ved behandling av søknad: ${packet.toJson()}" }
            søknadMetrikker.søknaderFeilet.increment()
            throw e
        }
    }

    companion object {
        private val logger = KotlinLogging.logger {}
        private val sikkerlogg = KotlinLogging.logger("tjenestekall")
    }

    private fun JsonMessage.tilHendelse(): SøknadHendelse {
        val ident = this["fødselsnummer"].asText()
        val dato = LocalDateTime.parse(this["datoRegistrert"].asText())

        val referanseId =
            if (!this[quizSøknadIdNøkkel].isMissingNode) {
                this[quizSøknadIdNøkkel].asText()
            } else if (!this[legacySøknadIdNøkkel].isMissingNode) {
                this[legacySøknadIdNøkkel].asText()
            } else {
                UUIDv7.newUuid().toString() // Papirsøknad har ikke referanseId, da må vi generere en random UUID
            }

        return SøknadHendelse(ident = ident, dato = dato, startDato = dato, referanseId = referanseId)
    }
}
