package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.core.instrument.MeterRegistry
import io.opentelemetry.instrumentation.annotations.WithSpan
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.SøknadMetrikker
import no.nav.dagpenger.rapportering.personregister.mediator.service.SøknadService
import no.nav.dagpenger.rapportering.personregister.mediator.utils.UUIDv7
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.SøknadHendelse
import java.time.LocalDateTime

private const val QUIZ_SØKNAD_ID_NØKKEL = "søknadsData.søknad_uuid"
private const val LEGACY_SØKNAD_ID_NØKKEL = "søknadsData.brukerBehandlingId"

private val logger = KotlinLogging.logger {}
private val sikkerlogg = KotlinLogging.logger("tjenestekall")

class SøknadMottak(
    rapidsConnection: RapidsConnection,
    private val søknadService: SøknadService,
    private val søknadMetrikker: SøknadMetrikker,
) : River.PacketListener {
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
                        QUIZ_SØKNAD_ID_NØKKEL,
                        LEGACY_SØKNAD_ID_NØKKEL,
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
        val ident = packet["fødselsnummer"].asText()

        logger.info { "Mottok innsending_ferdigstilt-melding" }
        sikkerlogg.info { "Mottok innsending_ferdigstilt-melding, ident=$ident: ${packet.toJson()}" }
        søknadMetrikker.søknaderMottatt.increment()

        try {
            søknadService.behandle(packet.tilHendelse())
        } catch (e: Exception) {
            logger.error(e) { "Feil ved behandling av innsending_ferdigstilt-melding" }
            sikkerlogg.error(e) { "Feil ved behandling av innsending_ferdigstilt-melding, ident=$ident: ${packet.toJson()}" }
            søknadMetrikker.søknaderFeilet.increment()
            throw e
        }
    }

    private fun JsonMessage.tilHendelse(): SøknadHendelse {
        val ident = this["fødselsnummer"].asText()
        val dato = LocalDateTime.parse(this["datoRegistrert"].asText())

        val referanseId =
            if (!this[QUIZ_SØKNAD_ID_NØKKEL].isMissingNode) {
                this[QUIZ_SØKNAD_ID_NØKKEL].asText()
            } else if (!this[LEGACY_SØKNAD_ID_NØKKEL].isMissingNode) {
                this[LEGACY_SØKNAD_ID_NØKKEL].asText()
            } else {
                UUIDv7.newUuid().toString() // Papirsøknad har ikke referanseId, da må vi generere en random UUID
            }

        return SøknadHendelse(ident = ident, dato = dato, startDato = dato, referanseId = referanseId)
    }
}
