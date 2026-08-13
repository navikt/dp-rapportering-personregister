package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.core.instrument.MeterRegistry
import io.opentelemetry.instrumentation.annotations.WithSpan
import no.nav.dagpenger.rapportering.personregister.mediator.MeldestatusMediator
import no.nav.dagpenger.rapportering.personregister.mediator.db.MeldingerRepository
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.MeldestatusMetrikker
import no.nav.dagpenger.rapportering.personregister.mediator.utils.UUIDv7
import no.nav.dagpenger.rapportering.personregister.modell.meldestatus.MeldestatusHendelse
import java.util.UUID

private val logger = KotlinLogging.logger {}
private val sikkerlogg = KotlinLogging.logger("tjenestekall")

class MeldestatusMottak(
    rapidsConnection: RapidsConnection,
    private val meldestatusMediator: MeldestatusMediator,
    private val meldestatusMetrikker: MeldestatusMetrikker,
    private val meldingerRepository: MeldingerRepository,
) : River.PacketListener {
    init {
        River(rapidsConnection)
            .apply {
                validate { it.requireValue("table", "ARENA_GOLDENGATE.MELDESTATUS") }
                validate { it.requireKey("after") }
                validate {
                    it.requireKey(
                        "@id",
                        "after.PERSON_ID",
                        "after.MELDESTATUS_ID",
                        "after.HENDELSE_ID",
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
        val arenaPersonId = packet["after"]["PERSON_ID"].asString()

        logger.info { "Mottok ny meldestatus-melding fra Arena" }
        sikkerlogg.info { "Mottok ny meldestatus-melding fra Arena, arenaPersonId=$arenaPersonId: ${packet.toJson()}" }
        meldestatusMetrikker.meldestatusMottatt.increment()

        try {
            val korrelasjonsId = UUIDv7.fromString(packet["@id"].asString())
            val hendelse = packet.tilHendelse(korrelasjonsId)

            val relevantMeldingsinnhold =
                """
                {
                    "@event_name": "meldestatus",
                    "arenaPersonId": "$arenaPersonId",
                    "meldestatusId": "${hendelse.meldestatusId}",
                    "hendelseId": "${hendelse.hendelseId}"
                }
                """.trimIndent()

            meldingerRepository.lagreInnkommendeMelding(
                korrelasjonsId = korrelasjonsId,
                ident = null,
                relevantMeldingsinnhold = relevantMeldingsinnhold,
            )

            meldestatusMediator.behandle(hendelse)
        } catch (e: Exception) {
            logger.error(e) { "Feil ved behandling av meldestatus-melding fra Arena" }
            sikkerlogg.error(e) { "Feil ved behandling av meldestatus-melding fra Arena, arenaPersonId=$arenaPersonId: ${packet.toJson()}" }
            meldestatusMetrikker.meldestatusFeilet.increment()
            throw e
        }
    }
}

private fun JsonMessage.tilHendelse(korrelasjonsId: UUID): MeldestatusHendelse =
    MeldestatusHendelse(
        korrelasjonsId = korrelasjonsId,
        personId = this["after"]["PERSON_ID"].asString().toLong(),
        meldestatusId = this["after"]["MELDESTATUS_ID"].asString().toLong(),
        hendelseId = this["after"]["HENDELSE_ID"].asString().toLong(),
    )
