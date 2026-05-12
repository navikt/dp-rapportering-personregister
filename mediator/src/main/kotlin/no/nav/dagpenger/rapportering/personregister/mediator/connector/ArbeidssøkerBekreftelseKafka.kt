package no.nav.dagpenger.rapportering.personregister.mediator.connector

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.kafka.utils.sendDeferred
import no.nav.dagpenger.rapportering.personregister.mediator.Configuration.bekreftelseTopic
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.ArbeidssøkerBekreftelseMelding
import no.nav.paw.bekreftelse.melding.v1.Bekreftelse
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import java.util.UUID

private val logger = KotlinLogging.logger { }
private val sikkerlogg = KotlinLogging.logger("tjenestekall")

class ArbeidssøkerBekreftelseKafka(
    private val bekreftelseKafkaProdusent: Producer<Long, Bekreftelse>,
) {
    suspend fun sendBekreftelse(
        recordKey: Long,
        arbeidssøkerBekreftelseMelding: ArbeidssøkerBekreftelseMelding,
    ): UUID {
        val periodeId = arbeidssøkerBekreftelseMelding.bekreftelse.periodeId

        logger.info { "Sender arbeidssøkerbekreftelse for periode $periodeId" }
        sikkerlogg.info { "Sender arbeidssøkerbekreftelse for periode $periodeId." }

        val bekreftelse = arbeidssøkerBekreftelseMelding.tilBekreftelse()
        val record = ProducerRecord(bekreftelseTopic, recordKey, bekreftelse)

        try {
            val metadata = bekreftelseKafkaProdusent.sendDeferred(record).await()
            logger.info {
                "Sendt arbeidssøkerbekreftelse for periode: $periodeId til Arbeidssøkerregisteret."
            }
            sikkerlogg.info {
                "Sendt arbeidssøkerbekreftelse for periode: $periodeId, ident: ${arbeidssøkerBekreftelseMelding.ident} til Arbeidssøkerregisteret."
                "Metadata: topic=${metadata.topic()} (partition=${metadata.partition()}, offset=${metadata.offset()})"
            }

            return arbeidssøkerBekreftelseMelding.bekreftelse.id
        } catch (e: Exception) {
            logger.error(e) {
                "Kunne ikke sende arbeidssøkerstatus for periode $periodeId til Arbeidssøkerregisteret."
            }
            sikkerlogg.error(e) {
                "Kunne ikke sende arbeidssøkerstatus for periode $periodeId til Arbeidssøkerregisteret. Melding forsøkt sendt: $bekreftelse"
            }
            throw e
        }
    }
}
