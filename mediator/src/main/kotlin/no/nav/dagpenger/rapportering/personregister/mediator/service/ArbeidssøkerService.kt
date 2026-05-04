package no.nav.dagpenger.rapportering.personregister.mediator.service

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.rapportering.personregister.kafka.utils.sendDeferred
import no.nav.dagpenger.rapportering.personregister.mediator.ApplicationBuilder.Companion.getRapidsConnection
import no.nav.dagpenger.rapportering.personregister.mediator.Configuration.bekreftelseTopic
import no.nav.dagpenger.rapportering.personregister.mediator.ZONE_ID
import no.nav.dagpenger.rapportering.personregister.mediator.connector.ArbeidssøkerConnector
import no.nav.dagpenger.rapportering.personregister.mediator.connector.MeldekortregisterConnector
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.ArbeidssøkerBekreftelseMelding
import no.nav.dagpenger.rapportering.personregister.modell.Arbeidssøkerperiode
import no.nav.paw.bekreftelse.melding.v1.Bekreftelse
import no.nav.paw.bekreftelse.melding.v1.vo.Bekreftelsesloesning
import no.nav.paw.bekreftelse.melding.v1.vo.Bruker
import no.nav.paw.bekreftelse.melding.v1.vo.BrukerType
import no.nav.paw.bekreftelse.melding.v1.vo.Metadata
import no.nav.paw.bekreftelse.melding.v1.vo.Svar
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import java.time.Instant
import java.util.UUID

private val sikkerlogg = KotlinLogging.logger("tjenestekall")

class ArbeidssøkerService(
    private val arbeidssøkerConnector: ArbeidssøkerConnector,
    private val meldekortregisterConnector: MeldekortregisterConnector,
    private val bekreftelseKafkaProdusent: Producer<Long, Bekreftelse>,
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
                runBlocking { meldekortregisterConnector.hentSisteInnsendteMeldekort() }

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

    suspend fun sendBekreftelse(bekreftelsesmelding: ArbeidssøkerBekreftelseMelding): UUID {
        val ident = bekreftelsesmelding.ident
        val gjelderFra = bekreftelsesmelding.bekreftelse.svar.gjelderFra
        val gjelderTil = bekreftelsesmelding.bekreftelse.svar.gjelderTil

        val recordKeyResponse = arbeidssøkerConnector.hentRecordKey(ident)

        val bruker = bekreftelsesmelding.bekreftelse.svar.sendtInnAv.utførtAv
        val årsak = bekreftelsesmelding.bekreftelse.svar.sendtInnAv.årsak

        val arbeidssøkerBekreftelse =
            Bekreftelse(
                bekreftelsesmelding.bekreftelse.periodeId,
                Bekreftelsesloesning.DAGPENGER,
                bekreftelsesmelding.bekreftelse.id,
                Svar(
                    Metadata(
                        Instant.now().atZone(ZONE_ID).toInstant(),
                        Bruker(
                            BrukerType.valueOf(bruker.type),
                            bruker.ident,
                            bruker.sikkerhetsnivå,
                        ),
                        Bekreftelsesloesning.DAGPENGER.name,
                        årsak,
                    ),
                    gjelderFra
                        .atZone(ZONE_ID)
                        .toInstant(),
                    gjelderTil
                        .plusDays(1)
                        .atZone(ZONE_ID)
                        .toInstant(),
                    bekreftelsesmelding.bekreftelse.svar.harJobbetIDennePerioden,
                    bekreftelsesmelding.bekreftelse.svar.vilFortsetteSomArbeidssøker,
                ),
            )

        try {
            val record = ProducerRecord(bekreftelseTopic, recordKeyResponse.key, arbeidssøkerBekreftelse)
            val metadata = bekreftelseKafkaProdusent.sendDeferred(record).await()
            sikkerlogg.info {
                "Sendt arbeidssøkerstatus for ident = $ident til Team PAW. " +
                    "Metadata: topic=${metadata.topic()} (partition=${metadata.partition()}, offset=${metadata.offset()})"
            }

            return bekreftelsesmelding.bekreftelse.id
        } catch (e: Exception) {
            logger.error(e) { "Kunne ikke sende arbeidssøkerstatus til Kafka" }

            throw Exception(e)
        }
    }
}
