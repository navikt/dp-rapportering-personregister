package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDate
import com.github.navikt.tbd_libs.rapids_and_rivers.isMissingOrNull
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.PersonstatusMediator
import no.nav.dagpenger.rapportering.personregister.modell.StansHendelse

class MeldegruppeendringMottak(
    rapidsConnection: RapidsConnection,
    private val personstatusMediator: PersonstatusMediator,
) : River.PacketListener {
    init {
        River(rapidsConnection)
            .apply {
                validate { it.requireValue("table", "ARENA_GOLDENGATE.MELDEGRUPPE") }
                validate { it.requireKey("FODSELSNR", "MELDEGRUPPEKODE", "DATO_FRA", "HENDELSE_ID") }
                validate { it.interestedIn("DATO_TIL") }
            }.register(this)
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry,
    ) {
        logger.info { "Mottok ny meldegruppeendring" }

        try {
            personstatusMediator
                .behandle(
                    packet
                        .tilHendelse(),
                )
        } catch (e: Exception) {
            logger.error(e) { "Feil ved behandling av meldegruppeendring $e" }
        }
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}

private fun JsonMessage.tilHendelse(): StansHendelse {
    val ident: String = this["FODSELSNR"].asText()
    val meldegruppeKode = this["MELDEGRUPPEKODE"].asText()
    val fraOgMed = this["DATO_FRA"].asLocalDate()
    val tilOgMed = if (this["DATO_TIL"].isMissingOrNull()) null else this["DATO_TIL"].asLocalDate()
    val hendelseId = this["HENDELSE_ID"].asText()

    return StansHendelse(
        ident = ident,
        dato = fraOgMed.atStartOfDay(),
        referanseId = hendelseId,
        meldegruppeKode = meldegruppeKode,
    )
}
