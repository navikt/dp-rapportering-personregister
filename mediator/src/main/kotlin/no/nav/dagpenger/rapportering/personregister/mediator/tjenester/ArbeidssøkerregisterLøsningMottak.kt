package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDateTime
import com.github.navikt.tbd_libs.rapids_and_rivers.withMDC
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.ArbeidssøkerMediator
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.BehovType.Arbeidssøkerstatus
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.BehovType.OvertaBekreftelse
import no.nav.dagpenger.rapportering.personregister.modell.Arbeidssøkerperiode
import no.nav.dagpenger.rapportering.personregister.modell.OvertaBekreftelseLøsning
import java.util.UUID

class ArbeidssøkerregisterLøsningMottak(
    rapidsConnection: RapidsConnection,
    private val arbeidssøkerMediator: ArbeidssøkerMediator,
) : River.PacketListener {
    init {
        River(rapidsConnection)
            .apply {
                validate {
                    it.requireValue("@event_name", "behov_arbeidssokerstatus")
                    it.requireKey("@løsning", "@feil", "ident")
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
                    Arbeidssøkerstatus -> arbeidssøkerMediator.behandle(packet.tilArbeidssøkerperiode())
                    OvertaBekreftelse -> arbeidssøkerMediator.behandle(packet.tilOvertaBekreftelseLøsning())
                }
            } catch (e: Exception) {
                logger.error(e) { "Feil ved behandling av behov" }
                sikkerlogg.error(e) { "Mottak av behov feilet" }
            }
        }
    }

    companion object {
        val logger = KotlinLogging.logger {}
        private val sikkerlogg = KotlinLogging.logger("tjenestekall.ArbeidssøkerregisterLøsningMottak")
    }
}

enum class BehovType {
    Arbeidssøkerstatus,
    OvertaBekreftelse,
}

fun JsonMessage.tilArbeidssøkerperiode(): Arbeidssøkerperiode =
    with(this["@løsning"]["Arbeidssøkerstatus"]["verdi"]) {
        Arbeidssøkerperiode(
            ident = this["ident"].asText(),
            periodeId = UUID.fromString(this["periodeId"].asText()),
            startet = this["startDato"].asLocalDateTime(),
            avsluttet = with(this["sluttDato"]) { if (isNull) null else asLocalDateTime() },
            overtattBekreftelse = null,
        )
    }

fun JsonMessage.tilOvertaBekreftelseLøsning(): OvertaBekreftelseLøsning =
    OvertaBekreftelseLøsning(
        ident = this["ident"].asText(),
        periodeId = UUID.fromString(this["periodeId"].asText()),
        løsning = with(this["@løsning"]["OvertaBekreftelse"]["verdi"]) { if (isNull) null else asText() },
        feil = with(this["@feil"]["OvertaBekreftelse"]["verdi"]) { if (isNull) null else asText() },
    )
