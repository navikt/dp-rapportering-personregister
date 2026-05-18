package no.nav.dagpenger.rapportering.personregister.mediator.service

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.ZONE_ID
import no.nav.dagpenger.rapportering.personregister.mediator.connector.ArbeidssøkerConnector
import no.nav.dagpenger.rapportering.personregister.mediator.connector.MeldekortregisterConnector
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.modell.Arbeidssøkerperiode
import java.time.LocalDateTime
import java.util.UUID

class ArbeidssøkerService(
    private val rapidsConnection: RapidsConnection,
    private val personRepository: PersonRepository,
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

    suspend fun publiserAvsluttetArbeidssøkerperiode(periode: Arbeidssøkerperiode) {
        logger.info { "Publiserer avsluttet arbeidssøkerperiode for periodeId ${periode.periodeId}" }
        val avregistrertTidspunkt = periode.hentAvregistrertTidspunkt()
        val periodeId = periode.periodeId
        val fastsattMeldedato = hentFastsattMeldedato(periodeId)
        val årsak = hentÅrsakEllerDefault(periodeId, periode.ident)

        val melding =
            avsluttetMelding(
                periode = periode,
                fastsattMeldedato = fastsattMeldedato,
                avregistrertTidspunkt = avregistrertTidspunkt,
                årsak = årsak,
            )

        publiser(periode, melding)
    }

    private fun Arbeidssøkerperiode.hentAvregistrertTidspunkt(): LocalDateTime =
        avsluttet ?: throw IllegalArgumentException(
            "Periode $periodeId er ikke avsluttet og kan ikke publiseres",
        )

    private fun publiser(
        periode: Arbeidssøkerperiode,
        melding: JsonMessage,
    ) {
        try {
            rapidsConnection.publish(periode.ident, melding.toJson())
            logger.info { "Publiserte avsluttet_arbeidssokerperiode for periodeId ${periode.periodeId}" }
        } catch (e: Exception) {
            logger.error(e) { "Feil ved publisering, periodeId=${periode.periodeId}" }
            sikkerLogg.error(e) { "Feil ved publisering, periodeId=${periode.periodeId}, ident=${periode.ident}" }
            throw e
        }
    }

    private fun hentÅrsakEllerDefault(
        periodeId: UUID,
        ident: String,
    ): Arbeidssøkerperiode.ÅrsakTilUtmelding =
        personRepository.hentÅrsakTilUtmelding(periodeId, ident)
            ?: Arbeidssøkerperiode.ÅrsakTilUtmelding.UTMELDT_I_ARBEIDSSØKERREGISTERET

    private suspend fun hentFastsattMeldedato(periodeId: UUID): LocalDateTime? {
        val fastsatt = meldekortregisterConnector.hentSisteInnsendteMeldekort()?.tilOgMed?.plusDays(1)
        logger.info { "fastsattMeldedato=$fastsatt for periodeId=$periodeId" }
        return fastsatt
    }

    private fun avsluttetMelding(
        periode: Arbeidssøkerperiode,
        fastsattMeldedato: LocalDateTime?,
        avregistrertTidspunkt: LocalDateTime,
        årsak: Arbeidssøkerperiode.ÅrsakTilUtmelding,
    ): JsonMessage =
        JsonMessage.newMessage(
            "avsluttet_arbeidssokerperiode",
            buildMap {
                put("ident", periode.ident)
                put("periodeId", periode.periodeId)
                put("avregistrertTidspunkt", avregistrertTidspunkt)
                put("årsak", årsak.dbValue)
                fastsattMeldedato?.let { put("fastsattMeldedato", it) }
            },
        )

    companion object {
        private val logger = KotlinLogging.logger {}
        private val sikkerLogg = KotlinLogging.logger("tjenestekall")
    }
}
