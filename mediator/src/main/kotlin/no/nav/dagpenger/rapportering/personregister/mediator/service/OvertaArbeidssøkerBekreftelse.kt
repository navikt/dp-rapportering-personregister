package no.nav.dagpenger.rapportering.personregister.mediator.service

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.BehovType

data class OvertaArbeidssøkerBekreftelseMelding(
    val ident: String,
    val periodeId: String,
) {
    fun asMessage(): JsonMessage =
        JsonMessage.newMessage(
            "behov_arbeissokerstatus",
            mutableMapOf(
                "ident" to ident,
                "periodeId" to periodeId,
                "@behov" to BehovType.OvertaBekreftelse.name,
            ),
        )
}

class OvertaArbeidssøkerBekreftelse(
    private val rapidsConnection: RapidsConnection,
) {
    private val logger = KotlinLogging.logger {}

    fun behandle(
        ident: String,
        periodeId: String,
    ) {
        val melding = OvertaArbeidssøkerBekreftelseMelding(ident, periodeId)
        try {
            rapidsConnection.publish(melding.asMessage().toJson())
            logger.info { "Bekreftelse for periodeId=$periodeId ble sendt" }
        } catch (e: Exception) {
            logger.error(e) { "Feil ved sending av bekreftelse for periodeId=$periodeId" }
        }
    }
}
