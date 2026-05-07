package no.nav.dagpenger.rapportering.personregister.mediator.service

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.connector.ArbeidssøkerBekreftelseConnector
import no.nav.dagpenger.rapportering.personregister.mediator.connector.ArbeidssøkerConnector
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.ArbeidssøkerBekreftelseMelding
import no.nav.dagpenger.rapportering.personregister.modell.Arbeidssøkerperiode
import java.util.UUID

private val sikkerlogg = KotlinLogging.logger("tjenestekall")

class ArbeidssøkerBekreftelseService(
    private val arbeidssøkerConnector: ArbeidssøkerConnector,
    private val arbeidssøkerBekreftelseConnector: ArbeidssøkerBekreftelseConnector,
    private val personRepository: PersonRepository,
) {
    suspend fun behandle(melding: ArbeidssøkerBekreftelseMelding) {
        try {
            sikkerlogg.info { "Behandle arbeidssøkerbekreftelse: $melding" }
            val recordKey = arbeidssøkerConnector.hentRecordKey(melding.ident).key

            val vilFortsetteSomArbeidssøker = melding.bekreftelse.svar.vilFortsetteSomArbeidssøker
            if (!vilFortsetteSomArbeidssøker) {
                lagreÅrsakTilUtmelding(
                    periodeId = melding.bekreftelse.periodeId,
                    ident = melding.ident,
                )
            }
            arbeidssøkerBekreftelseConnector.sendBekreftelse(recordKey, melding)
        } catch (e: Exception) {
            sikkerlogg.error(e) { "Feil ved behandling av arbeidssøkerbekreftelse for ident ${melding.ident}" }
            throw e
        }
    }

    private fun lagreÅrsakTilUtmelding(
        periodeId: UUID,
        ident: String,
    ) {
        try {
            sikkerlogg.info { "Lagrer årsak til utmelding for periodeId $periodeId" }
            personRepository.lagreÅrsakTilUtmelding(periodeId, ident, Arbeidssøkerperiode.ÅrsakTilUtmelding.UTMELDT_PÅ_MELDEKORT)
        } catch (e: Exception) {
            sikkerlogg.error(e) { "Feil ved lagring av årsak til utmelding for ident $ident og periodeId $periodeId" }
            throw e
        }
    }
}
