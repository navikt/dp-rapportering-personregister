package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDate
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.core.instrument.MeterRegistry
import io.opentelemetry.instrumentation.annotations.WithSpan
import no.nav.dagpenger.rapportering.personregister.mediator.PersonMediator
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.MeldesyklusErPassertHendelse
import java.time.LocalDateTime

class MeldesyklusErPassertMottak(
    rapidsConnection: RapidsConnection,
    private val personMediator: PersonMediator,
) : River.PacketListener {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    init {
        River(rapidsConnection)
            .apply {
                precondition {
                    it.requireValue("@event_name", "meldesyklus_er_passert")
                }
                validate {
                    it.requireKey(
                        "ident",
                        "dato",
                        "referanseId",
                        "meldekortregisterPeriodeId",
                        "periodeFraOgMed",
                        "periodeTilOgMed",
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
        logger.info { "Mottok meldesyklus_er_passert melding" }

        val ident = packet["ident"].asText()
        val referanseId = packet["referanseId"].asText()
        val meldekortregisterPeriodeId = packet["meldekortregisterPeriodeId"].asText()
        val periodeFraOgMed = packet["periodeFraOgMed"].asLocalDate()
        val periodeTilOgMed = packet["periodeTilOgMed"].asLocalDate()

        if (!ident.matches(Regex("[0-9]{11}"))) {
            logger.error("Person-ident må ha 11 sifre")
            return
        }

        val meldesyklusErPassertHendelse =
            MeldesyklusErPassertHendelse(
                ident,
                LocalDateTime.now(),
                LocalDateTime.now(),
                referanseId,
                meldekortregisterPeriodeId,
                periodeFraOgMed,
                periodeTilOgMed,
            )

        personMediator.behandle(meldesyklusErPassertHendelse)
    }
}
