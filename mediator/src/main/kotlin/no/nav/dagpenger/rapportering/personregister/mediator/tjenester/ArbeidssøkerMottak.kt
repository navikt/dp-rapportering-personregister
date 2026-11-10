package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import io.github.oshai.kotlinlogging.KotlinLogging
import io.opentelemetry.instrumentation.annotations.WithSpan
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.rapportering.personregister.mediator.ArbeidssøkerMediator
import no.nav.dagpenger.rapportering.personregister.mediator.Configuration.defaultObjectMapper
import no.nav.dagpenger.rapportering.personregister.mediator.ZONE_ID
import no.nav.dagpenger.rapportering.personregister.mediator.db.MeldingerRepository
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.ArbeidssøkerperiodeMetrikker
import no.nav.dagpenger.rapportering.personregister.mediator.service.ArbeidssøkerService
import no.nav.dagpenger.rapportering.personregister.modell.Arbeidssøkerperiode
import no.nav.dagpenger.rapportering.personregister.modell.avregistrert
import no.nav.paw.arbeidssokerregisteret.api.v1.Periode
import java.time.LocalDateTime
import java.util.UUID

private val logger = KotlinLogging.logger {}
private val sikkerLogg = KotlinLogging.logger("tjenestekall")

class ArbeidssøkerMottak(
    private val arbeidssøkerMediator: ArbeidssøkerMediator,
    private val arbeidssøkerperiodeMetrikker: ArbeidssøkerperiodeMetrikker,
    private val arbeidssøkerService: ArbeidssøkerService,
    private val meldingerRepository: MeldingerRepository,
) {
    @WithSpan
    fun consume(
        korrelasjonsId: UUID,
        periode: Periode,
    ) {
        try {
            logger.info { "Mottok periode-melding fra Arbeidssøkerregisteret med periodeId=${periode.id}" }
            sikkerLogg.info {
                "Mottok periode-melding fra Arbeidssøkerregisteret med periodeId=${periode.id}, ident=${periode.identitetsnummer}"
            }
            arbeidssøkerperiodeMetrikker.arbeidssøkerperiodeMottatt.increment()

            val arbeidssøkerperiode = periode.tilArbeidssøkerperiode()

            val relevantMeldingsinnhold =
                """
                {
                    "@event_name": "mottok_arbeidssøkerperiode",
                    "arbeidssøkerperiode": ${defaultObjectMapper.writeValueAsString(arbeidssøkerperiode)}
                }
                """.trimIndent()

            meldingerRepository.lagreInnkommendeMelding(
                korrelasjonsId = korrelasjonsId,
                ident = periode.identitetsnummer,
                relevantMeldingsinnhold = relevantMeldingsinnhold,
            )

            arbeidssøkerMediator.behandle(arbeidssøkerperiode, korrelasjonsId)

            if (arbeidssøkerperiode.avregistrert()) {
                runBlocking {
                    arbeidssøkerService.publiserAvsluttetArbeidssøkerperiode(
                        arbeidssøkerperiode.ident,
                        arbeidssøkerperiode.periodeId,
                        korrelasjonsId,
                    )
                }
            }
        } catch (e: Exception) {
            logger.error(
                e,
            ) {
                "Feil ved behandling av periode-melding fra Arbeidssøkerregisteret med periodeId=${periode.id}"
            }
            sikkerLogg.error(e) {
                "Feil ved behandling av periode-melding fra Arbeidssøkerregisteret med periodeId=${periode.id}, ident=${periode.identitetsnummer}"
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
