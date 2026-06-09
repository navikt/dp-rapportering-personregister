package no.nav.dagpenger.rapportering.personregister.mediator.service

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.connector.ArbeidssøkerBekreftelseKafka
import no.nav.dagpenger.rapportering.personregister.mediator.connector.ArbeidssøkerConnector
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.ArbeidssøkerBekreftelseMelding
import no.nav.dagpenger.rapportering.personregister.modell.Arbeidssøkerperiode
import no.nav.dagpenger.rapportering.personregister.modell.overtattBekreftelse
import java.util.UUID

private val logger = KotlinLogging.logger { }
private val sikkerlogg = KotlinLogging.logger("tjenestekall")

class ArbeidssøkerBekreftelseService(
    private val arbeidssøkerConnector: ArbeidssøkerConnector,
    private val arbeidssøkerBekreftelseKafka: ArbeidssøkerBekreftelseKafka,
    private val personRepository: PersonRepository,
) {
    suspend fun behandle(arbeidssøkerBekreftelseMelding: ArbeidssøkerBekreftelseMelding) {
        val periodeId = arbeidssøkerBekreftelseMelding.bekreftelse.periodeId
        val ident = arbeidssøkerBekreftelseMelding.ident

        try {
            logger.info {
                "Behandler arbeidssøkerbekreftelse for periode: $periodeId"
            }
            sikkerlogg.info { "Behandle arbeidssøkerbekreftelse: $arbeidssøkerBekreftelseMelding" }
            val recordKey = arbeidssøkerConnector.hentRecordKey(ident).key

            val person =
                personRepository.hentPerson(ident)
                    ?: throw IllegalStateException("Kunne ikke hente person for å sjekke ansvar for arbeidssøkerbekreftelse")

            if (!person.overtattBekreftelse) {
                logger.info {
                    "Dagpenger har ikke ansvar for arbeidssøkerbekreftelse for personen. Bekreftelsesmelding for periode $periodeId behandles ikke."
                }
                sikkerlogg.info {
                    "Dagpenger har ikke ansvar for arbeidssøkerbekreftelse for person med ident=$ident. Bekreftelsesmelding for periode $periodeId behandles ikke."
                }
                return
            }

            val vilFortsetteSomArbeidssøker =
                arbeidssøkerBekreftelseMelding.bekreftelse.svar.vilFortsetteSomArbeidssøker
            if (!vilFortsetteSomArbeidssøker) {
                lagreÅrsakTilUtmelding(
                    periodeId = periodeId,
                    ident = ident,
                )
            }
            arbeidssøkerBekreftelseKafka.sendBekreftelse(recordKey, arbeidssøkerBekreftelseMelding)
        } catch (e: Exception) {
            logger.error(e) {
                "Feil ved behandling av arbeidssøkerbekreftelse for periode: $periodeId"
            }
            sikkerlogg.error(e) {
                "Feil ved behandling av arbeidssøkerbekreftelse for periode: $periodeId. Melding: $arbeidssøkerBekreftelseMelding"
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
            logger.error(e) { "Feil ved lagring av årsak til utmelding for periodeId $periodeId" }
            sikkerlogg.error(e) { "Feil ved lagring av årsak til utmelding for ident $ident og periodeId $periodeId" }
            throw e
        }
    }
}
