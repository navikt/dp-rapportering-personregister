package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.core.instrument.MeterRegistry
import io.opentelemetry.instrumentation.annotations.WithSpan
import no.nav.dagpenger.rapportering.personregister.mediator.db.MeldingerRepository
import no.nav.dagpenger.rapportering.personregister.mediator.jobs.AktiverHendelserJob
import no.nav.dagpenger.rapportering.personregister.mediator.utils.UUIDv7

private val logger = KotlinLogging.logger {}

internal class StartAktiverHendelserJobManueltMottak(
    val rapidsConnection: RapidsConnection,
    val aktiverHendelserJob: AktiverHendelserJob,
    val meldingerRepository: MeldingerRepository,
) : River.PacketListener {
    init {
        River(rapidsConnection)
            .apply {
                precondition { it.requireValue("@event_name", "ramp_start_aktiver_hendelser_job_manuelt") }
                validate {
                    it.requireKey(
                        "@id",
                    )
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
        logger.info { "Mottok melding om at AktiverHendelserJob skal startes manuelt" }

        meldingerRepository.lagreInnkommendeMelding(
            korrelasjonsId = UUIDv7.fromString(packet["@id"].asString()),
            ident = null,
            relevantMeldingsinnhold =
                """
                {
                    "@event_name": "${packet["@event_name"].asString()}"
                }
                """.trimIndent(),
        )

        aktiverHendelserJob.execute()
    }
}
