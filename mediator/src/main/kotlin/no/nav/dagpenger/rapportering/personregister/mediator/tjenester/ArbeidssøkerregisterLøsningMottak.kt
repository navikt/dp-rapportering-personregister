package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import com.fasterxml.jackson.module.kotlin.readValue
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.withMDC
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.Configuration.defaultObjectMapper
import no.nav.dagpenger.rapportering.personregister.mediator.PersonstatusMediator
import no.nav.dagpenger.rapportering.personregister.mediator.hendelser.ArbeidssøkerHendelse
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.BehovType.Arbeidssøkerstatus
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.BehovType.OvertaBekreftelse

class ArbeidssøkerregisterLøsningMottak(
    rapidsConnection: RapidsConnection,
    private val personstatusMediator: PersonstatusMediator,
) : River.PacketListener {
    init {
        River(rapidsConnection)
            .apply {
                validate {
                    it.requireValue("@event_name", "behov_arbeissokerstatus")
                    it.requireKey("@løsning", "ident")
                    it.requireAllOrAny("@behov", BehovType.entries.map { behov -> behov.toString() })
                    it.interestedIn("periodeId", "arbeidssøkerNestePeriode", "arbeidet", "meldeperiode")
                }
            }.register(this)
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry,
    ) {
        withMDC(
            mapOf("ident" to packet["ident"].asText()),
        ) {
            sikkerlogg.info { "Mottok behov: ${packet.toJson()}" }
            try {
                val behov = BehovType.valueOf(packet.get("@behov")[0].asText())
                when (behov) {
                    Arbeidssøkerstatus -> personstatusMediator.behandle(packet.tilArbeidssøkerHendelse())
                    OvertaBekreftelse ->
                        TODO(
                            "Hvordan behandler vi svar om overtagelse av bekreftelse? Og skal vi egentlig behandle dette?",
                        )
                }
            } catch (e: Exception) {
                sikkerlogg.error(e) { "Mottak av behov feilet" }
            }
        }
    }

    companion object {
        private val logger = KotlinLogging.logger {}
        private val sikkerlogg = KotlinLogging.logger("tjenestekall.ArbeidssøkerregisterLøsningMottak")
    }
}

enum class BehovType {
    Arbeidssøkerstatus,
    OvertaBekreftelse,
}

fun JsonMessage.tilArbeidssøkerHendelse(): ArbeidssøkerHendelse =
    defaultObjectMapper.readValue<ArbeidssøkerHendelse>(this["@løsning"]["Arbeidssøkerstatus"]["verdi"].toString())
