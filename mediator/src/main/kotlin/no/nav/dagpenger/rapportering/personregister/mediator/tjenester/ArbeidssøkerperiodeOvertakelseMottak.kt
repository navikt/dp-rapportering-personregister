package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import io.opentelemetry.instrumentation.annotations.WithSpan
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.ArbeidssøkerMediator
import no.nav.paw.bekreftelse.paavegneav.v1.PaaVegneAv
import no.nav.paw.bekreftelse.paavegneav.v1.vo.Bekreftelsesloesning
import org.apache.kafka.clients.consumer.ConsumerRecords

class ArbeidssøkerperiodeOvertakelseMottak(
    private val arbeidssøkerMediator: ArbeidssøkerMediator,
) {
    @WithSpan
    fun consume(records: ConsumerRecords<Long, PaaVegneAv>) =
        records.forEach { record ->
            logger.info { "Tar imot overtakelse av periode med periodeId ${record.value().periodeId}" }
            // arbeidssøkerperiodeMetrikker.arbeidssøkerperiodeMottatt.increment()
            with(record.value()) {
                if (this.bekreftelsesloesning != Bekreftelsesloesning.DAGPENGER) {
                    logger.warn(
                        "Bekreftelsesløsning i melding om overtakelse av perioden ${this.periodeId} var ${this.bekreftelsesloesning}.",
                    )
                } else {
                    arbeidssøkerMediator.behandle(this)
                }
            }
        }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
