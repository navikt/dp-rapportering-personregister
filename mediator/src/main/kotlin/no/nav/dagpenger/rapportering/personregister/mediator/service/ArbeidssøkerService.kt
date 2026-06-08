package no.nav.dagpenger.rapportering.personregister.mediator.service

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.ZONE_ID
import no.nav.dagpenger.rapportering.personregister.mediator.connector.ArbeidssøkerConnector
import no.nav.dagpenger.rapportering.personregister.mediator.connector.MeldekortregisterConnector
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.AvsluttetArbeidssøkerperiodeMetrikker
import no.nav.dagpenger.rapportering.personregister.modell.AnsvarligSystem
import no.nav.dagpenger.rapportering.personregister.modell.Arbeidssøkerperiode
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class ArbeidssøkerService(
    private val personRepository: PersonRepository,
    private val arbeidssøkerConnector: ArbeidssøkerConnector,
    private val meldekortregisterConnector: MeldekortregisterConnector,
    private val rapidsConnection: () -> RapidsConnection,
    private val avsluttetArbeidssøkerperiodeMetrikker: AvsluttetArbeidssøkerperiodeMetrikker,
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
        personRepository.hentPerson(periode.ident)?.let { person ->
            if (person.ansvarligSystem == AnsvarligSystem.DP) {
                logger.info {
                    "Bruker har ansvarligSystem == DP, publiserer avsluttet arbeidssøkerperiode for periodeId ${periode.periodeId}"
                }
                val avregistrertTidspunkt = periode.hentAvregistrertTidspunkt()
                val periodeId = periode.periodeId
                val fastsattMeldedato = hentFastsattMeldedato(periode.ident, periodeId)
                val årsak = hentÅrsakEllerDefault(periodeId, periode.ident)

                val melding =
                    avsluttetMelding(
                        periode = periode,
                        fastsattMeldedato = fastsattMeldedato,
                        avregistrertTidspunkt = avregistrertTidspunkt,
                        årsak = årsak,
                    )

                publiser(periode, melding)
            } else {
                logger.info {
                    "Bruker har ansvarligSystem != DP, publiserer ikke avsluttet arbeidssøkerperiode for periodeId ${periode.periodeId}"
                }
            }
        }
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
            rapidsConnection().publish(periode.ident, melding.toJson())
            avsluttetArbeidssøkerperiodeMetrikker.avsluttetArbeidssøkerperiodeSendt.increment()
            logger.info { "Publiserte avsluttet_arbeidssokerperiode for periodeId ${periode.periodeId}" }
        } catch (e: Exception) {
            avsluttetArbeidssøkerperiodeMetrikker.avsluttetArbeidssøkerperiodeUtsendingFeilet.increment()
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

    private suspend fun hentFastsattMeldedato(
        ident: String,
        periodeId: UUID,
    ): LocalDate? {
        val fastsatt = meldekortregisterConnector.hentSisteFastsattMeldedato(ident)
        logger.info { "fastsattMeldedato=$fastsatt for periodeId=$periodeId" }
        return fastsatt
    }

    private fun avsluttetMelding(
        periode: Arbeidssøkerperiode,
        fastsattMeldedato: LocalDate?,
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
