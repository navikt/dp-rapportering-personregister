package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.core.instrument.MeterRegistry
import io.opentelemetry.instrumentation.annotations.WithSpan
import no.nav.dagpenger.rapportering.personregister.mediator.service.ManuellMigreringAvMeldekortForBrukereSomBareHarAvslagService

private val logger = KotlinLogging.logger {}

class ManuellMigreringAvMeldekortForBrukereSomBareHarAvslagMottak(
    val rapidsConnection: RapidsConnection,
    private val manuellMigreringAvMeldekortForBrukereSomBareHarAvslagService: ManuellMigreringAvMeldekortForBrukereSomBareHarAvslagService,
) : River.PacketListener {
    init {
        River(rapidsConnection)
            .apply {
                precondition { it.requireValue("@event_name", "migrer_meldekort_for_brukere_som_bare_har_avslag") }
                validate {
                    it.requireKey("@id", "personIder")
                }
            }.register(this)
    }

    @WithSpan
    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry,
    ) {
        logger.info { "Mottok melding om at meldekort skal migreres for brukere som bare har avslag" }

        try {
            manuellMigreringAvMeldekortForBrukereSomBareHarAvslagService.migrerMeldekort(
                packet["personIder"].asArray()?.toList()?.map { it.asLong() },
            )
        } catch (e: Exception) {
            logger.error(e) { "Feil ved behandling av melding om at meldekort skal migreres for brukere som bare har avslag." }
        }
    }
}
