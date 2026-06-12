package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import io.getunleash.Unleash
import io.github.oshai.kotlinlogging.KotlinLogging
import io.opentelemetry.instrumentation.annotations.WithSpan
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.rapportering.personregister.mediator.ArbeidssøkerMediator
import no.nav.dagpenger.rapportering.personregister.mediator.ZONE_ID
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.ArbeidssøkerperiodeMetrikker
import no.nav.dagpenger.rapportering.personregister.mediator.service.ArbeidssøkerService
import no.nav.dagpenger.rapportering.personregister.modell.Arbeidssøkerperiode
import no.nav.dagpenger.rapportering.personregister.modell.avregistrert
import no.nav.paw.arbeidssokerregisteret.api.v1.Periode
import org.apache.kafka.clients.consumer.ConsumerRecords
import java.time.LocalDateTime

private val logger = KotlinLogging.logger {}
private val sikkerLogg = KotlinLogging.logger("tjenestekall")

class ArbeidssøkerMottak(
    private val arbeidssøkerMediator: ArbeidssøkerMediator,
    private val arbeidssøkerperiodeMetrikker: ArbeidssøkerperiodeMetrikker,
    private val arbeidssøkerService: ArbeidssøkerService,
    private val unleash: Unleash,
) {
    @WithSpan
    fun consume(records: ConsumerRecords<Long, Periode>) =
        records.forEach { record ->
            val periode = record.value()

            try {
                logger.info { "Mottok periode-melding fra Arbeidssøkerregisteret med key=${record.key()}, periodeId=${periode.id}" }
                sikkerLogg.info {
                    "Mottok periode-melding fra Arbeidssøkerregisteret med key=${record.key()}, periodeId=${periode.id}, ident=${periode.identitetsnummer}"
                }
                arbeidssøkerperiodeMetrikker.arbeidssøkerperiodeMottatt.increment()

                val arbeidssøkerperiode = periode.tilArbeidssøkerperiode()

                if (arbeidssøkerperiode.avregistrert()) {
                    if (unleash.isEnabled("dp-rapportering-personregister-publiser-avsluttet-arbeidssokerperiode")) {
                        runBlocking { arbeidssøkerService.publiserAvsluttetArbeidssøkerperiode(arbeidssøkerperiode) }
                    } else {
                        logger.info { "Publisering av avsluttet arbeidssøkerperiode er deaktivert" }
                    }
                }

                arbeidssøkerMediator.behandle(arbeidssøkerperiode)
            } catch (e: Exception) {
                logger.error(
                    e,
                ) {
                    "Feil ved behandling av periode-melding fra Arbeidssøkerregisteret med key=${record.key()}, periodeId=${periode.id}"
                }
                sikkerLogg.error(e) {
                    "Feil ved behandling av periode-melding fra Arbeidssøkerregisteret med key=${record.key()}, periodeId=${periode.id}, ident=${periode.identitetsnummer}"
                }
                arbeidssøkerperiodeMetrikker.arbeidssøkerperiodeFeilet.increment()
                throw e
            }
        }

    private fun Periode.tilArbeidssøkerperiode() =
        Arbeidssøkerperiode(
            ident = identitetsnummer,
            periodeId = id,
            startet = LocalDateTime.ofInstant(startet.tidspunkt, ZONE_ID),
            avsluttet = avsluttet?.let { LocalDateTime.ofInstant(it.tidspunkt, ZONE_ID) },
            overtattBekreftelse = null,
        )
}
