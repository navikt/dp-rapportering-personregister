package no.nav.dagpenger.rapportering.personregister.mediator.service

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.connector.ArbeidssøkerBekreftelseConnector
import no.nav.dagpenger.rapportering.personregister.mediator.connector.ArbeidssøkerConnector
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.ArbeidssøkerBekreftelseMelding
import no.nav.dagpenger.rapportering.personregister.modell.Arbeidssøkerperiode
import java.util.UUID

private val logger = KotlinLogging.logger { }
private val sikkerlogg = KotlinLogging.logger("tjenestekall")

class ArbeidssøkerBekreftelseService(
    private val arbeidssøkerConnector: ArbeidssøkerConnector,
    private val arbeidssøkerBekreftelseConnector: ArbeidssøkerBekreftelseConnector,
    private val personRepository: PersonRepository,
) {
    suspend fun behandle(arbeidssøkerBekreftelseMelding: ArbeidssøkerBekreftelseMelding) {
        try {
            sikkerlogg.info { "Behandle arbeidssøkerbekreftelse: $arbeidssøkerBekreftelseMelding" }
            logger.info {
                "Behandle arbeidssøkerbekreftelse for periode: ${arbeidssøkerBekreftelseMelding.bekreftelse.periodeId}, ident: ${arbeidssøkerBekreftelseMelding.ident}"
            }
            val recordKey = arbeidssøkerConnector.hentRecordKey(arbeidssøkerBekreftelseMelding.ident).key

            val vilFortsetteSomArbeidssøker =
                arbeidssøkerBekreftelseMelding.bekreftelse.svar.vilFortsetteSomArbeidssøker
            if (!vilFortsetteSomArbeidssøker) {
                lagreÅrsakTilUtmelding(
                    periodeId = arbeidssøkerBekreftelseMelding.bekreftelse.periodeId,
                    ident = arbeidssøkerBekreftelseMelding.ident,
                )
            }
            arbeidssøkerBekreftelseConnector.sendBekreftelse(recordKey, arbeidssøkerBekreftelseMelding)
        } catch (e: Exception) {
            logger.error(e) {
                "Feil ved behandling av arbeidssøkerbekreftelse for periode: ${arbeidssøkerBekreftelseMelding.bekreftelse.periodeId}, ident ${arbeidssøkerBekreftelseMelding.ident}"
            }
            sikkerlogg.error(e) {
                "Feil ved behandling av arbeidssøkerbekreftelse for periode: ${arbeidssøkerBekreftelseMelding.bekreftelse.periodeId}, ident ${arbeidssøkerBekreftelseMelding.ident}"
            }
            throw e
        }
    }

    private fun lagreÅrsakTilUtmelding(
        periodeId: UUID,
        ident: String,
    ) {
        try {
            logger.info { "Lagrer årsak til utmelding for periodeId $periodeId" }
            sikkerlogg.info { "Lagrer årsak til utmelding for periodeId $periodeId" }
            personRepository.lagreÅrsakTilUtmelding(
                periodeId,
                ident,
                Arbeidssøkerperiode.ÅrsakTilUtmelding.UTMELDT_PÅ_MELDEKORT,
            )
        } catch (e: Exception) {
            logger.error(e) { "Feil ved lagring av årsak til utmelding for ident $ident og periodeId $periodeId" }
            sikkerlogg.error(e) { "Feil ved lagring av årsak til utmelding for ident $ident og periodeId $periodeId" }
            throw e
        }
    }
}
