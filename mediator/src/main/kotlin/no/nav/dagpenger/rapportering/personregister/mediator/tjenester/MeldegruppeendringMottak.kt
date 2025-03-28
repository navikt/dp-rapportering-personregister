package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.isMissingOrNull
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import io.opentelemetry.instrumentation.annotations.WithSpan
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.FremtidigHendelseMediator
import no.nav.dagpenger.rapportering.personregister.mediator.PersonMediator
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.MeldegruppeendringMetrikker
import no.nav.dagpenger.rapportering.personregister.modell.AnnenMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.DagpengerMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.Hendelse
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.text.get

class MeldegruppeendringMottak(
    rapidsConnection: RapidsConnection,
    private val personMediator: PersonMediator,
    private val fremtidigHendelseMediator: FremtidigHendelseMediator,
    private val meldegruppeendringMetrikker: MeldegruppeendringMetrikker,
) : River.PacketListener {
    init {
        River(rapidsConnection)
            .apply {
                validate { it.requireValue("table", "ARENA_GOLDENGATE.MELDEGRUPPE") }
                validate { it.requireKey("after", "after.STATUS_AKTIV") }
                validate {
                    it.requireKey(
                        "after.FODSELSNR",
                        "after.MELDEGRUPPEKODE",
                        "after.DATO_FRA",
                        "after.HENDELSE_ID",
                    )
                }
                validate { it.interestedIn("after.DATO_TIL", "after.HAR_MELDT_SEG") }
                validate { it.forbidValue("after.STATUS_AKTIV", "N") }
            }.register(this)
    }

    @WithSpan
    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry,
    ) {
        logger.info { "Mottok ny meldegruppeendring" }

        try {
            when (val hendelse = packet.tilHendelse()) {
                is DagpengerMeldegruppeHendelse -> {
                    if (hendelse.startDato.isAfter(LocalDateTime.now())) {
                        meldegruppeendringMetrikker.fremtidigMeldegruppeMottatt.increment()
                        fremtidigHendelseMediator.behandle(hendelse)
                    } else {
                        meldegruppeendringMetrikker.dagpengerMeldegruppeMottatt.increment()
                        personMediator.behandle(hendelse)
                    }
                }
                is AnnenMeldegruppeHendelse -> {
                    if (hendelse.startDato.isAfter(LocalDateTime.now())) {
                        meldegruppeendringMetrikker.fremtidigMeldegruppeMottatt.increment()
                        fremtidigHendelseMediator.behandle(hendelse)
                    } else {
                        meldegruppeendringMetrikker.annenMeldegruppeMottatt.increment()
                        personMediator.behandle(hendelse)
                    }
                }
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
    val dato =
        if (this["after"]["HENDELSESDATO"].isMissingOrNull()) {
            LocalDateTime.now()
        } else {
            this["after"]["HENDELSESDATO"]
                .asText()
                .arenaDato()
        }
    val startDato = this["after"]["DATO_FRA"].asText().arenaDato()
    val sluttDato = if (this["after"]["DATO_TIL"].isMissingOrNull()) null else this["after"]["DATO_TIL"].asText().arenaDato()
    val hendelseId = this["after"]["HENDELSE_ID"].asText()
    val harMeldtSeg =
        if (this["after"]["HAR_MELDT_SEG"]?.isMissingOrNull() != false) {
            true
        } else {
            this["after"]["HAR_MELDT_SEG"].asText() == "J"
        }
    val arenaId = this["after"]["MELDEGRUPPE_ID"].asInt()

    if (meldegruppeKode == "DAGP") {
        return DagpengerMeldegruppeHendelse(
            ident = ident,
            dato = dato,
            startDato = startDato,
            sluttDato = sluttDato,
            referanseId = hendelseId,
            meldegruppeKode = meldegruppeKode,
            harMeldtSeg = harMeldtSeg,
            arenaId = arenaId,
        )
    }

    return AnnenMeldegruppeHendelse(
        ident = ident,
        dato = dato,
        startDato = startDato,
        sluttDato = sluttDato,
        referanseId = hendelseId,
        meldegruppeKode = meldegruppeKode,
        harMeldtSeg = harMeldtSeg,
        arenaId = arenaId,
    )
}

fun String.arenaDato(): LocalDateTime {
    val pattern = "yyyy-MM-dd HH:mm:ss"
    val formatter = DateTimeFormatter.ofPattern(pattern)
    return LocalDateTime.parse(this, formatter)
}
