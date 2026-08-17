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
import no.nav.dagpenger.rapportering.personregister.mediator.db.MeldingerRepository
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.IkkeMeldtSegPå21DagerMetrikker
import no.nav.dagpenger.rapportering.personregister.mediator.utils.UUIDv7
import no.nav.dagpenger.rapportering.personregister.mediator.utils.validerIdent
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.IkkeMeldtSegPå21DagerHendelse
import java.time.LocalDateTime

class IkkeMeldtSegPå21DagerMottak(
    rapidsConnection: RapidsConnection,
    private val personMediator: PersonMediator,
    private val ikkeMeldtSegPå21DagerMetrikker: IkkeMeldtSegPå21DagerMetrikker,
    private val meldingerRepository: MeldingerRepository,
) : River.PacketListener {
    companion object {
        private val logger = KotlinLogging.logger {}
        private val sikkerlogg = KotlinLogging.logger("tjenestekall")
    }

    init {
        River(rapidsConnection)
            .apply {
                precondition {
                    it.requireAny("@event_name", listOf("meldesyklus_er_passert", "ikke_meldt_seg_på_21_dager"))
                }
                validate {
                    it.requireKey(
                        "@id",
                        "ident",
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
        val ident = packet["ident"].asString()

        logger.info { "Mottok ikke_meldt_seg_på_21_dager-melding" }
        sikkerlogg.info { "Mottok ikke_meldt_seg_på_21_dager-melding, ident=$ident: ${packet.toJson()}" }
        ikkeMeldtSegPå21DagerMetrikker.ikkeMeldtSegPå21DagerMottatt.increment()

        try {
            val korrelasjonsId = UUIDv7.fromString(packet["@id"].asString())
            val referanseId = packet["referanseId"].asString()

            ident.validerIdent()

            val ikkeMeldtSegPå21DagerHendelse =
                IkkeMeldtSegPå21DagerHendelse(
                    korrelasjonsId = korrelasjonsId,
                    ident = ident,
                    dato = LocalDateTime.now(),
                    startDato = LocalDateTime.now(),
                    referanseId = referanseId,
                )

            val relevantMeldingsinnhold =
                """
                {
                    "@event_name": "${packet["@event_name"].asString()}",
                    "referanseId": "$referanseId"
                }
                """.trimIndent()

            meldingerRepository.lagreInnkommendeMelding(
                korrelasjonsId = korrelasjonsId,
                ident = ident,
                relevantMeldingsinnhold = relevantMeldingsinnhold,
            )

            personMediator.behandle(ikkeMeldtSegPå21DagerHendelse)
        } catch (e: Exception) {
            logger.error(e) { "Feil ved behandling av ikke_meldt_seg_på_21_dager-melding" }
            sikkerlogg.error(e) { "Feil ved behandling av ikke_meldt_seg_på_21_dager-melding, ident=$ident: ${packet.toJson()}" }
            ikkeMeldtSegPå21DagerMetrikker.ikkeMeldtSegPå21DagerFeilet.increment()
            throw e
        }
    }
}
