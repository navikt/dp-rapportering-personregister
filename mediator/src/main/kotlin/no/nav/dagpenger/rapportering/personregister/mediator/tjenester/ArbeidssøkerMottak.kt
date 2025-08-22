package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import io.github.oshai.kotlinlogging.KotlinLogging
import io.opentelemetry.instrumentation.annotations.WithSpan
import no.nav.dagpenger.rapportering.personregister.mediator.ArbeidssøkerMediator
import no.nav.dagpenger.rapportering.personregister.mediator.ZONE_ID
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.ArbeidssøkerperiodeMetrikker
import no.nav.dagpenger.rapportering.personregister.modell.Arbeidssøkerperiode
import no.nav.paw.arbeidssokerregisteret.api.v1.Periode
import org.apache.kafka.clients.consumer.ConsumerRecords
import java.time.LocalDateTime

class ArbeidssøkerMottak(
    private val arbeidssøkerMediator: ArbeidssøkerMediator,
    private val arbeidssøkerperiodeMetrikker: ArbeidssøkerperiodeMetrikker,
) {
    @WithSpan
    fun consume(records: ConsumerRecords<Long, Periode>) =
        records.forEach { record ->
            logger.info { "Behandler periode med key: ${record.key()}" }
            arbeidssøkerperiodeMetrikker.arbeidssøkerperiodeMottatt.increment()
            Arbeidssøkerperiode(
                ident = record.value().identitetsnummer,
                periodeId = record.value().id,
                startet = LocalDateTime.ofInstant(record.value().startet.tidspunkt, ZONE_ID),
                avsluttet = record.value().avsluttet?.let { LocalDateTime.ofInstant(it.tidspunkt, ZONE_ID) },
                overtattBekreftelse = null,
            ).also(arbeidssøkerMediator::behandle)
        }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
