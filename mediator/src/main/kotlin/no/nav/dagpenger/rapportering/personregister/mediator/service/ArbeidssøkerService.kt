package no.nav.dagpenger.rapportering.personregister.mediator.service

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.BehovType
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.BehovType.Arbeidssøkerstatus
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.BehovType.OvertaBekreftelse

class ArbeidssøkerService(
    private val rapidsConnection: RapidsConnection,
) {
    fun sendOvertaBekreftelseBehov(
        ident: String,
        periodeId: String,
    ) {
        publiserBehov(OvertaBekreftelseBehov(ident, periodeId))
    }

    fun sendArbeidssøkerBehov(ident: String) {
        publiserBehov(ArbeidssøkerstatusBehov(ident))
        logger.info { "Publiserte behov for arbeidssøkerstatus for ident $ident" }
    }

    private fun publiserBehov(behov: Behovmelding) {
        // sikkerlogg.info { "Publiserer behov ${behov.behovType} for ident ${behov.ident}" }
        rapidsConnection.publish(behov.tilMelding().toJson())
    }

    companion object {
        val logger = KotlinLogging.logger {}
        // val sikkerlogg = KotlinLogging.logger("tjenestekall.HentRapporteringperioder")
    }
}

sealed class Behovmelding(
    open val ident: String,
    val behovType: BehovType,
) {
    abstract fun tilMelding(): JsonMessage
}

data class ArbeidssøkerstatusBehov(
    override val ident: String,
) : Behovmelding(ident, Arbeidssøkerstatus) {
    override fun tilMelding(): JsonMessage =
        JsonMessage.newMessage(
            "behov_arbeissokerstatus",
            mutableMapOf(
                "@behov" to behovType.name,
                "ident" to ident,
            ),
        )
}

data class OvertaBekreftelseBehov(
    override val ident: String,
    val periodeId: String,
) : Behovmelding(ident, OvertaBekreftelse) {
    override fun tilMelding(): JsonMessage =
        JsonMessage.newMessage(
            "behov_arbeissokerstatus",
            mutableMapOf(
                "@behov" to behovType.name,
                "ident" to ident,
                "periodeId" to periodeId,
            ),
        )
}
