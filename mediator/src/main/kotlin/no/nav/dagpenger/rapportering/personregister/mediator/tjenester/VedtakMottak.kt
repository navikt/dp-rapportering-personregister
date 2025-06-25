package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDate
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import io.opentelemetry.instrumentation.annotations.WithSpan
import mu.withLoggingContext
import no.nav.dagpenger.rapportering.personregister.mediator.FremtidigHendelseMediator
import no.nav.dagpenger.rapportering.personregister.mediator.PersonMediator
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.VedtakMetrikker
import no.nav.dagpenger.rapportering.personregister.modell.VedtakHendelse
import java.time.LocalDate
import java.time.LocalDateTime

class VedtakMottak(
    rapidsConnection: RapidsConnection,
    private val personMediator: PersonMediator,
    private val fremtidigHendelseMediator: FremtidigHendelseMediator,
    private val vedtakMetrikker: VedtakMetrikker,
) : River.PacketListener {
    companion object {
        private val logger = mu.KotlinLogging.logger {}
    }

    init {
        River(rapidsConnection)
            .apply {
                precondition {
                    it.requireValue("@event_name", "vedtak_fattet")
                    it.requireValue("behandletHendelse.type", "Søknad")
                }
                validate { it.requireKey("behandlingId", "ident", "virkningsdato") }
            }.register(this)
    }

    @WithSpan
    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry,
    ) {
        val behandlingId = packet["behandlingId"].asText()

        withLoggingContext(
            "behandlingId" to behandlingId,
            "event_name" to "vedtak_fattet",
        ) {
            logger.info { "Mottok vedtak_fattet melding" }
            vedtakMetrikker.vedtakMottatt.increment()

            val ident = packet["ident"].asText()
            val virkningsdato = packet["virkningsdato"].asLocalDate()

            if (!ident.matches(Regex("[0-9]{11}"))) {
                logger.error("Person-ident må ha 11 sifre")
                return
            }

            val vedtakHendelse =
                VedtakHendelse(
                    ident,
                    LocalDateTime.now(),
                    virkningsdato.atStartOfDay(),
                    behandlingId,
                )

            if (virkningsdato.isAfter(LocalDate.now())) {
                fremtidigHendelseMediator.behandle(vedtakHendelse)
            } else {
                personMediator.behandle(vedtakHendelse)
            }
        }
    }
}
