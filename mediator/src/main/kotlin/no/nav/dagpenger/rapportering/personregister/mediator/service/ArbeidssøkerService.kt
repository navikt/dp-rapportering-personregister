package no.nav.dagpenger.rapportering.personregister.mediator.service

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.rapportering.personregister.mediator.ApplicationBuilder.Companion.getRapidsConnection
import no.nav.dagpenger.rapportering.personregister.mediator.ZONE_ID
import no.nav.dagpenger.rapportering.personregister.mediator.connector.ArbeidssøkerConnector
import no.nav.dagpenger.rapportering.personregister.mediator.connector.MeldekortregisterConnector
import no.nav.dagpenger.rapportering.personregister.modell.Arbeidssøkerperiode

class ArbeidssøkerService(
    private val arbeidssøkerConnector: ArbeidssøkerConnector,
    private val meldekortregisterConnector: MeldekortregisterConnector,
) {
    suspend fun hentSisteArbeidssøkerperiode(ident: String): Arbeidssøkerperiode? =
        arbeidssøkerConnector.hentSisteArbeidssøkerperiode(ident).firstOrNull()?.let {
            Arbeidssøkerperiode(
                periodeId = it.periodeId,
                startet =
                    it.startet
                        .tidspunkt
                        .atZoneSameInstant(ZONE_ID)
                        .toLocalDateTime(),
                avsluttet =
                    it.avsluttet
                        ?.tidspunkt
                        ?.atZoneSameInstant(ZONE_ID)
                        ?.toLocalDateTime(),
                ident = ident,
                overtattBekreftelse = null,
            )
        }

    fun publiserAvsluttetArbeidssøkerperiode(arbeidssøkerperiode: Arbeidssøkerperiode) {
        logger.info { "Publiserer avsluttet arbeidssøkerperiode for periodeId ${arbeidssøkerperiode.periodeId}" }

        val avsluttetTidspunkt =
            requireNotNull(arbeidssøkerperiode.avsluttet) {
                "Kan ikke publisere avsluttet periode for en periode som ikke er avsluttet"
            }
        val ident = arbeidssøkerperiode.ident

        try {
            val sisteInnsendteMeldekort =
                runBlocking { meldekortregisterConnector.hentSisteInnsendteMeldekort(ident) }

            val fastsattMeldingsdag = sisteInnsendteMeldekort?.tilOgMed?.plusDays(1)

            logger.info { "fastsattMeldingsdag: $fastsattMeldingsdag for periodeId ${arbeidssøkerperiode.periodeId}" }

            val message =
                JsonMessage.newMessage(
                    "avsluttet_arbeidssokerperiode",
                    buildMap {
                        put("ident", ident)
                        put("avsluttetTidspunkt", avsluttetTidspunkt)
                        fastsattMeldingsdag?.let { put("fastsattMeldingsdag", it) }
                    },
                )

            getRapidsConnection().publish(ident, message.toJson())
            logger.info { "Publiserte avsluttet_arbeidssokerperiode for periodeId ${arbeidssøkerperiode.periodeId}" }
        } catch (e: Exception) {
            logger.error(e) { "Feil ved publisering av avsluttet arbeidssøkerperiode for periodeId ${arbeidssøkerperiode.periodeId}" }
            sikkerLogg.error(
                e,
            ) { "Feil ved publisering av avsluttet arbeidssøkerperiode for periodeId ${arbeidssøkerperiode.periodeId}, ident $ident" }
            throw e
        }
    }

    companion object {
        private val logger = KotlinLogging.logger {}
        private val sikkerLogg = KotlinLogging.logger("tjenestekall")
    }
}
