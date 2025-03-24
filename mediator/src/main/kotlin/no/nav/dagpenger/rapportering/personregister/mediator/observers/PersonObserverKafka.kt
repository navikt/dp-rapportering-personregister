package no.nav.dagpenger.rapportering.personregister.mediator.observers

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.kafka.utils.sendDeferred
import no.nav.dagpenger.rapportering.personregister.mediator.connector.ArbeidssøkerConnector
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.PersonObserver
import no.nav.dagpenger.rapportering.personregister.modell.gjeldende
import no.nav.paw.bekreftelse.paavegneav.v1.PaaVegneAv
import no.nav.paw.bekreftelse.paavegneav.v1.vo.Bekreftelsesloesning.DAGPENGER
import no.nav.paw.bekreftelse.paavegneav.v1.vo.Start
import no.nav.paw.bekreftelse.paavegneav.v1.vo.Stopp
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import java.util.concurrent.TimeUnit.DAYS

class PersonObserverKafka(
    private val producer: Producer<Long, PaaVegneAv>,
    private val arbeidssøkerConnector: ArbeidssøkerConnector,
    private val bekreftelsePåVegneAvTopic: String,
) : PersonObserver {
    override fun frasiArbeidssøkerBekreftelse(person: Person) {
        try {
            person
                .arbeidssøkerperioder
                .gjeldende
                ?.periodeId
                ?.let { periodeId ->
                    val recordKey = runBlocking { arbeidssøkerConnector.hentRecordKey(person.ident) }
                    val record =
                        ProducerRecord(
                            bekreftelsePåVegneAvTopic,
                            recordKey.key,
                            PaaVegneAv(periodeId, DAGPENGER, Stopp()),
                        )
                    Span.current().addEvent(
                        "Frasier ansvar for arbeidssøkerbekreftelse",
                        Attributes.of(AttributeKey.stringKey("periodeId"), periodeId.toString()),
                    )
                    val metadata = runBlocking { producer.sendDeferred(record).await() }
                    logger.info {
                        "Sendte melding om at vi frasier oss ansvaret for bekreftelse av periodeId" +
                            " $periodeId til arbeidssøkerregisteret. " +
                            "Metadata: topic=${metadata.topic()} " +
                            "(partition=${metadata.partition()}, offset=${metadata.offset()})"
                    }
                }
        } catch (e: Exception) {
            logger.error(e) { "Feil ved frasigelse av bekreftelse" }
        }
    }

    override fun overtaArbeidssøkerBekreftelse(person: Person) {
        try {
            person
                .arbeidssøkerperioder
                .gjeldende
                ?.periodeId
                ?.let { periodeId ->
                    val recordKeyResponse = runBlocking { arbeidssøkerConnector.hentRecordKey(person.ident) }
                    val record =
                        ProducerRecord(
                            bekreftelsePåVegneAvTopic,
                            recordKeyResponse.key,
                            PaaVegneAv(
                                periodeId,
                                DAGPENGER,
                                Start(
                                    DAYS.toMillis(14),
                                    DAYS.toMillis(8),
                                ),
                            ),
                        )

                    Span.current().addEvent(
                        "Overtar ansvar for arbeidssøkerbekreftelse",
                        Attributes.of(AttributeKey.stringKey("periodeId"), periodeId.toString()),
                    )
                    val metadata = runBlocking { producer.sendDeferred(record).await() }
                    logger.info {
                        "Sendte melding om at vi overtar ansvaret for bekreftelse av periodeId $periodeId til arbeidssøkerregisteret. " +
                            "Metadata: topic=${metadata.topic()} (partition=${metadata.partition()}, offset=${metadata.offset()})"
                    }
                }
                ?: run { logger.info { "Fant ingen aktiv arbeidssøkerperiode for person" } }
        } catch (e: Exception) {
            logger.error(e) { "Feil ved overtagelse av bekreftelse" }
        }
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
