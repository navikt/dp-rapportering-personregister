package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDate
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.getunleash.Unleash
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.oshai.kotlinlogging.withLoggingContext
import io.micrometer.core.instrument.MeterRegistry
import io.opentelemetry.instrumentation.annotations.WithSpan
import no.nav.dagpenger.rapportering.personregister.mediator.FremtidigHendelseMediator
import no.nav.dagpenger.rapportering.personregister.mediator.PersonMediator
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.BehandlingsresultatMetrikker
import no.nav.dagpenger.rapportering.personregister.mediator.utils.validerIdent
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.VedtakHendelse
import java.time.LocalDate
import java.time.LocalDateTime

class BehandlingsresultatMottak(
    rapidsConnection: RapidsConnection,
    private val personRepository: PersonRepository,
    private val personMediator: PersonMediator,
    private val fremtidigHendelseMediator: FremtidigHendelseMediator,
    private val behandlingsresultatMetrikker: BehandlingsresultatMetrikker,
    private val unleash: Unleash,
) : River.PacketListener {
    companion object {
        private val logger = KotlinLogging.logger {}
        private val sikkerlogg = KotlinLogging.logger("tjenestekall")
    }

    init {
        River(rapidsConnection)
            .apply {
                precondition {
                    it.requireValue("@event_name", "behandlingsresultat")
                }
                validate {
                    it.requireKey(
                        "behandletHendelse",
                        "behandlingId",
                        "ident",
                        "automatisk",
                        "rettighetsperioder",
                    )
                }
                validate { it.forbidValue("regelverk", "Ferietillegg") }
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
            "event_name" to "behandlingsresultat",
        ) {
            logger.info { "Mottok behandlingsresultat melding" }
            sikkerlogg.info { "Mottok behandlingsresultat melding: ${packet.toJson()}" }
            behandlingsresultatMetrikker.behandlingsresultatMottatt.increment()

            try {
                val ident = packet["ident"].asText()

                ident.validerIdent()

                // Slett fremtidige VedtakHendelser
                personRepository.slettFremtidigeVedtakHendelser(ident)

                packet["rettighetsperioder"]
                    .toList()
                    .sortedBy { it["fraOgMed"].asLocalDate() }
                    .forEachIndexed { index, rettighetsperiode ->
                        val opprinnelse = rettighetsperiode["opprinnelse"].asText()
                        val fraOgMed = rettighetsperiode["fraOgMed"].asLocalDate()
                        val tilOgMed = rettighetsperiode["tilOgMed"]?.asLocalDate()
                        val harRett = rettighetsperiode["harRett"].asBoolean()

                        // Vi sletter ikke eksisterende VedtakHendelser. Da kan vi ha hele vedtak-historikken i databasen
                        // Men vi prosesserer ikke Arvet-rettighetsperioder (disse er allerede behandlet)
                        // Vi oppretter VedtakHendelse for hver ikke Arvet-rettighetsperiode slik at personregister kan vurdere ny status iht den siste versjonen av sannheten fra PJ
                        if (unleash.isEnabled("dp-rapportering-personregister-ikke-prosesser-arvet") && opprinnelse == "Arvet") {
                            logger.info { "Rettighetsperioder fra $fraOgMed til $tilOgMed er Arvet" }
                            sikkerlogg.info { "Rettighetsperioder fra $fraOgMed til $tilOgMed er Arvet" }
                            return@forEachIndexed
                        }

                        val vedtakHendelse =
                            VedtakHendelse(
                                ident = ident,
                                dato = LocalDateTime.now(),
                                startDato = fraOgMed.atStartOfDay(),
                                sluttDato = tilOgMed?.atStartOfDay(),
                                referanseId = "$behandlingId-$index",
                                utfall = harRett,
                            )

                        if (fraOgMed.isAfter(LocalDate.now())) {
                            fremtidigHendelseMediator.behandle(vedtakHendelse)
                        } else {
                            personMediator.behandle(vedtakHendelse)
                        }
                    }
            } catch (e: Exception) {
                logger.error(e) { "Feil ved behandling av behandlingsresultat" }
                sikkerlogg.error(e) { "Feil ved behandling av behandlingsresultat: ${packet.toJson()}" }
                behandlingsresultatMetrikker.behandlingsresultatFeilet.increment()
                throw e
            }
        }
    }
}
