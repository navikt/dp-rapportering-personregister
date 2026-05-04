package no.nav.dagpenger.rapportering.personregister.mediator.connector

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.kafka.utils.sendDeferred
import no.nav.dagpenger.rapportering.personregister.mediator.Configuration.bekreftelseTopic
import no.nav.dagpenger.rapportering.personregister.mediator.ZONE_ID
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.ArbeidssøkerBekreftelseMelding
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

private val logger = KotlinLogging.logger { }
private val sikkerlogg = KotlinLogging.logger("tjenestekall")

class ArbeidssøkerBekreftelseConnector(
    private val bekreftelseKafkaProdusent: Producer<Long, Bekreftelse>,
) {
    suspend fun sendBekreftelse(
        recordKey: Long,
        bekreftelsesmelding: ArbeidssøkerBekreftelseMelding,
    ): UUID {
        val ident = bekreftelsesmelding.ident
        val gjelderFra = bekreftelsesmelding.bekreftelse.svar.gjelderFra
        val gjelderTil = bekreftelsesmelding.bekreftelse.svar.gjelderTil

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
            val record = ProducerRecord(bekreftelseTopic, recordKey, arbeidssøkerBekreftelse)
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
