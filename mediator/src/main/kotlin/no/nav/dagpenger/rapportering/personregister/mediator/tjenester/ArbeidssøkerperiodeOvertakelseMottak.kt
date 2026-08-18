package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import io.github.oshai.kotlinlogging.KotlinLogging
import io.opentelemetry.instrumentation.annotations.WithSpan
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.rapportering.personregister.mediator.ArbeidssøkerMediator
import no.nav.dagpenger.rapportering.personregister.mediator.db.MeldingerRepository
import no.nav.dagpenger.rapportering.personregister.mediator.utils.UUIDv7
import no.nav.paw.bekreftelse.paavegneav.v1.PaaVegneAv
import no.nav.paw.bekreftelse.paavegneav.v1.vo.Bekreftelsesloesning
import no.nav.paw.bekreftelse.paavegneav.v1.vo.Start
import no.nav.paw.bekreftelse.paavegneav.v1.vo.Stopp
import org.apache.kafka.clients.consumer.ConsumerRecords

class ArbeidssøkerperiodeOvertakelseMottak(
    private val arbeidssøkerMediator: ArbeidssøkerMediator,
    private val meldingerRepository: MeldingerRepository,
) {
    @WithSpan
    fun consume(records: ConsumerRecords<Long, PaaVegneAv>) {
        val korrelasjonsId = UUIDv7.newUuid()

        records.forEach { record ->
            val value = record.value()

            logger.info { "Tar imot overtakelse av periode med periodeId ${value.periodeId}" }

            val handling =
                when (value.handling) {
                    is Start -> {
                        "Start"
                    }

                    is Stopp -> {
                        "Stopp"
                    }

                    else -> {
                        "Ukjent handling"
                    }
                }
            val relevantMeldingsinnhold =
                """
                {
                    "@event_name": "mottok_arbeidssøkerperiode_overtakelse",
                    "paVegneAv": {
                        "periodeId": "${value.periodeId}",
                        "bekreftelsesloesning": "${value.bekreftelsesloesning}",
                        "handling": "$handling"
                    }   
                }
                """.trimIndent()

            meldingerRepository.lagreInnkommendeMelding(
                korrelasjonsId = korrelasjonsId,
                ident = null,
                relevantMeldingsinnhold = relevantMeldingsinnhold,
            )

            with(value) {
                if (this.bekreftelsesloesning != Bekreftelsesloesning.DAGPENGER) {
                    logger.warn {
                        "Bekreftelsesløsning i melding om overtakelse av perioden ${this.periodeId} var ${this.bekreftelsesloesning}."
                    }
                } else {
                    runBlocking { arbeidssøkerMediator.behandle(this@with) }
                }
            }
        }
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
