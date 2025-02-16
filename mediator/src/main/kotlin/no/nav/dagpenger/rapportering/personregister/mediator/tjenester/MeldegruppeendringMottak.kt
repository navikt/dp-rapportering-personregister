package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.PersonstatusMediator
import no.nav.dagpenger.rapportering.personregister.modell.AnnenMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.DagpengerMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.Hendelse
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class MeldegruppeendringMottak(
    rapidsConnection: RapidsConnection,
    private val personstatusMediator: PersonstatusMediator,
) : River.PacketListener {
    init {
        River(rapidsConnection)
            .apply {
                validate { it.requireValue("table", "ARENA_GOLDENGATE.MELDEGRUPPE") }
                validate { it.requireKey("after") }
                validate { it.requireKey("after.FODSELSNR", "after.MELDEGRUPPEKODE", "after.DATO_FRA", "after.HENDELSE_ID") }
                validate { it.interestedIn("after.DATO_TIL") }
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
            when (val hendelse = packet.tilHendelse()) {
                is DagpengerMeldegruppeHendelse -> personstatusMediator.behandle(hendelse)
                is AnnenMeldegruppeHendelse -> personstatusMediator.behandle(hendelse)
                else -> logger.warn { "Ukjent hendelsetype mottatt: $hendelse" }
            }
        } catch (e: Exception) {
            logger.error(e) { "Feil ved behandling av meldegruppeendring $e" }
        }
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}

private fun JsonMessage.tilHendelse(): Hendelse {
    val ident: String = this["after"]["FODSELSNR"].asText()
    val meldegruppeKode = this["after"]["MELDEGRUPPEKODE"].asText()
    val fraOgMed = this["after"]["DATO_FRA"].asText().arenaDato()
    val hendelseId = this["after"]["HENDELSE_ID"].asText()

    if (meldegruppeKode == "DAGP") {
        return DagpengerMeldegruppeHendelse(
            ident = ident,
            dato = fraOgMed,
            referanseId = hendelseId,
            meldegruppeKode = meldegruppeKode,
        )
    }

    return AnnenMeldegruppeHendelse(
        ident = ident,
        dato = fraOgMed,
        referanseId = hendelseId,
        meldegruppeKode = meldegruppeKode,
    )
}

fun String.arenaDato(): LocalDateTime {
    val pattern = "yyyy-MM-dd HH:mm:ss"
    val formatter = DateTimeFormatter.ofPattern(pattern)
    return LocalDateTime.parse(this, formatter)
}
