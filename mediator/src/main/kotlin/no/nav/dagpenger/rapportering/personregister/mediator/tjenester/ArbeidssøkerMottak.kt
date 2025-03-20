package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import io.opentelemetry.instrumentation.annotations.WithSpan
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.ArbeidssøkerMediator
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.ArbeidssøkerperiodeMetrikker
import no.nav.dagpenger.rapportering.personregister.modell.Arbeidssøkerperiode
import no.nav.paw.arbeidssokerregisteret.api.v1.Periode
import org.apache.kafka.clients.consumer.ConsumerRecords
import java.time.LocalDateTime
import java.time.ZoneId

private val ZONE_ID = ZoneId.of("Europe/Oslo")

class ArbeidssøkerMottak(
    private val ArbeidssøkerMediator: ArbeidssøkerMediator,
    private val arbeidssøkerperiodeMetrikker: ArbeidssøkerperiodeMetrikker,
) {
    @WithSpan
    fun consume(records: ConsumerRecords<Long, Periode>) =
        records.forEach {
            logger.info { "Behandler periode med key: ${it.key()}" }
            arbeidssøkerperiodeMetrikker.arbeidssøkerperiodeMottatt.increment()
            Arbeidssøkerperiode(
                ident = it.value().identitetsnummer,
                periodeId = it.value().id,
                startet = LocalDateTime.ofInstant(it.value().startet.tidspunkt, ZONE_ID),
                avsluttet = it.value().avsluttet?.let { LocalDateTime.ofInstant(it.tidspunkt, ZONE_ID) },
                overtattBekreftelse = null,
            ).also(ArbeidssøkerMediator::behandle)
        }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
