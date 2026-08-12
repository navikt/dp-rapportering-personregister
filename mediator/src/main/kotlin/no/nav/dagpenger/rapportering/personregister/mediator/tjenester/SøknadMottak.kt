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
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.SøknadMetrikker
import no.nav.dagpenger.rapportering.personregister.mediator.service.SøknadService
import no.nav.dagpenger.rapportering.personregister.mediator.utils.UUIDv7
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.SøknadHendelse
import java.time.LocalDateTime
import java.util.UUID

private const val QUIZ_SØKNAD_ID_NØKKEL = "søknadsData.søknad_uuid"
private const val LEGACY_SØKNAD_ID_NØKKEL = "søknadsData.brukerBehandlingId"

private val logger = KotlinLogging.logger {}
private val sikkerlogg = KotlinLogging.logger("tjenestekall")

class SøknadMottak(
    rapidsConnection: RapidsConnection,
    private val søknadService: SøknadService,
    private val søknadMetrikker: SøknadMetrikker,
    private val meldingerRepository: MeldingerRepository,
) : River.PacketListener {
    init {
        River(rapidsConnection)
            .apply {
                precondition { it.requireValue("@event_name", "innsending_ferdigstilt") }
                validate {
                    it.requireKey(
                        "@id",
                        "fødselsnummer",
                        "datoRegistrert",
                    )
                    it.requireAny("type", listOf("NySøknad", "Gjenopptak"))
                    it.interestedIn(
                        QUIZ_SØKNAD_ID_NØKKEL,
                        LEGACY_SØKNAD_ID_NØKKEL,
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
        val ident = packet["fødselsnummer"].asString()

        logger.info { "Mottok innsending_ferdigstilt-melding" }
        sikkerlogg.info { "Mottok innsending_ferdigstilt-melding, ident=$ident: ${packet.toJson()}" }
        søknadMetrikker.søknaderMottatt.increment()

        try {
            val korrelasjonsId = UUIDv7.fromString(packet["@id"].asString())
            val hendelse = packet.tilHendelse(korrelasjonsId)

            val relevantMeldingsinnhold =
                """
                {
                    "@event_name": "${packet["@event_name"].asString()}",
                    "datoRegistrert": "${packet["datoRegistrert"].asString()}",
                    "referanseId": "${hendelse.referanseId}",
                    "type": "${packet["type"].asString()}"
                }
                """.trimIndent()

            meldingerRepository.lagreInnkommendeMelding(
                korrelasjonsId = korrelasjonsId,
                ident = ident,
                relevantMeldingsinnhold = relevantMeldingsinnhold,
            )

            søknadService.behandle(hendelse)
        } catch (e: Exception) {
            logger.error(e) { "Feil ved behandling av innsending_ferdigstilt-melding" }
            sikkerlogg.error(e) { "Feil ved behandling av innsending_ferdigstilt-melding, ident=$ident: ${packet.toJson()}" }
            søknadMetrikker.søknaderFeilet.increment()
            throw e
        }
    }

    private fun JsonMessage.tilHendelse(korrelasjonsId: UUID): SøknadHendelse {
        val ident = this["fødselsnummer"].asString()
        val dato = LocalDateTime.parse(this["datoRegistrert"].asString())

        val referanseId =
            if (!this[QUIZ_SØKNAD_ID_NØKKEL].isMissingNode) {
                this[QUIZ_SØKNAD_ID_NØKKEL].asString()
            } else if (!this[LEGACY_SØKNAD_ID_NØKKEL].isMissingNode) {
                this[LEGACY_SØKNAD_ID_NØKKEL].asString()
            } else {
                UUIDv7.newUuid().toString() // Papirsøknad har ikke referanseId, da må vi generere en random UUID
            }

        return SøknadHendelse(
            korrelasjonsId = korrelasjonsId,
            ident = ident,
            dato = dato,
            startDato = dato,
            referanseId = referanseId,
        )
    }
}
