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
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.SoknadMetrikker
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.SøknadHendelse
import java.time.LocalDateTime
import java.util.UUID

class SøknadMottak(
    rapidsConnection: RapidsConnection,
    private val personStatusMediator: PersonMediator,
    private val soknadMetrikker: SoknadMetrikker,
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
        soknadMetrikker.soknaderMottatt.increment()

        try {
            personStatusMediator.behandle(packet.tilHendelse())
        } catch (e: Exception) {
            logger.error(e) { "Feil ved behandling av søknad $e" }
        }
    }

    companion object {
        private val logger = KotlinLogging.logger {}
        private val sikkerlogg = KotlinLogging.logger("tjenestekall.SøknadMottak")
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
                UUID.randomUUID().toString() // Papirsøknad har ikke referanseId, da må vi generere en random UUID
            }

        return SøknadHendelse(ident = ident, dato = dato, startDato = dato, referanseId = referanseId)
    }
}
