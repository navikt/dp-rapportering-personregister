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

class ArbeidssøkerBekreftelseConnector(
    private val bekreftelseKafkaProdusent: Producer<Long, Bekreftelse>,
) {
    suspend fun sendBekreftelse(
        recordKey: Long,
        bekreftelsesmelding: ArbeidssøkerBekreftelseMelding,
    ): UUID {
        val bekreftelse = BekreftelseMapper.tilBekreftelse(bekreftelsesmelding)
        val record = ProducerRecord(bekreftelseTopic, recordKey, bekreftelse)

        try {
            val metadata = bekreftelseKafkaProdusent.sendDeferred(record).await()
            sikkerlogg.info {
                "Sendt arbeidssøkerbekreftelse for ident = ${bekreftelsesmelding.ident} til Team PAW. " +
                    "Metadata: topic=${metadata.topic()} (partition=${metadata.partition()}, offset=${metadata.offset()})"
            }

            return bekreftelsesmelding.bekreftelse.id
        } catch (e: Exception) {
            logger.error(e) { "Kunne ikke sende arbeidssøkerstatus til Kafka" }

            throw e
        }
    }
}
