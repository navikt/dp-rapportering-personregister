package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.core.instrument.MeterRegistry
import io.opentelemetry.instrumentation.annotations.WithSpan
import no.nav.dagpenger.rapportering.personregister.mediator.PersonMediator
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.IkkeMeldtSegPå21DagerMetrikker
import no.nav.dagpenger.rapportering.personregister.mediator.utils.validerIdent
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.IkkeMeldtSegPå21DagerHendelse
import java.time.LocalDateTime

class IkkeMeldtSegPå21DagerMottak(
    rapidsConnection: RapidsConnection,
    private val personMediator: PersonMediator,
    private val ikkeMeldtSegPå21DagerMetrikker: IkkeMeldtSegPå21DagerMetrikker,
) : River.PacketListener {
    companion object {
        private val logger = KotlinLogging.logger {}
        private val sikkerlogg = KotlinLogging.logger("tjenestekall")
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
        val ident = packet["ident"].asText()

        logger.info { "Mottok meldesyklus_er_passert-melding" }
        sikkerlogg.info { "Mottok meldesyklus_er_passert-melding, ident=$ident: ${packet.toJson()}" }
        ikkeMeldtSegPå21DagerMetrikker.ikkeMeldtSegPå21DagerMottatt.increment()

        try {
            val referanseId = packet["referanseId"].asText()

            ident.validerIdent()

            val ikkeMeldtSegPå21DagerHendelse =
                IkkeMeldtSegPå21DagerHendelse(
                    korrelasjonsId = null, // TODO:
                    ident = ident,
                    dato = LocalDateTime.now(),
                    startDato = LocalDateTime.now(),
                    referanseId = referanseId,
                )

            personMediator.behandle(ikkeMeldtSegPå21DagerHendelse)
        } catch (e: Exception) {
            logger.error(e) { "Feil ved behandling av meldesyklus_er_passert-melding" }
            sikkerlogg.error(e) { "Feil ved behandling av meldesyklus_er_passert-melding, ident=$ident: ${packet.toJson()}" }
            ikkeMeldtSegPå21DagerMetrikker.ikkeMeldtSegPå21DagerFeilet.increment()
            throw e
        }
    }
}
