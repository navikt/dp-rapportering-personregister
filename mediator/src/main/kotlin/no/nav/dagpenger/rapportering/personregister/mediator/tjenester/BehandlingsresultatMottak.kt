package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDate
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.oshai.kotlinlogging.withLoggingContext
import io.micrometer.core.instrument.MeterRegistry
import io.opentelemetry.instrumentation.annotations.WithSpan
import no.nav.dagpenger.rapportering.personregister.mediator.FremtidigHendelseMediator
import no.nav.dagpenger.rapportering.personregister.mediator.PersonMediator
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.BehandlingsresultatMetrikker
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.VedtakHendelse
import java.time.LocalDate
import java.time.LocalDateTime

class BehandlingsresultatMottak(
    rapidsConnection: RapidsConnection,
    private val personRepository: PersonRepository,
    private val personMediator: PersonMediator,
    private val fremtidigHendelseMediator: FremtidigHendelseMediator,
    private val behandlingsresultatMetrikker: BehandlingsresultatMetrikker,
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
                validate { it.forbidValue("behandlingId", "019a0650-20e8-7db4-a756-6ed0706ef26a") }
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

                if (!ident.matches(Regex("[0-9]{11}"))) {
                    throw IllegalArgumentException("Person-ident må ha 11 sifre")
                }

                // Slett fremtidige VedtakHendelser
                personRepository.slettFremtidigeVedtakHendelser(ident)

                packet["rettighetsperioder"]
                    .toList()
                    .sortedBy { it["fraOgMed"].asLocalDate() }
                    .forEachIndexed { index, rettighetsperiode ->
                        //  Vi sletter ikke eksisterende VedtahHendelser. Da kan vi ha hele vedtak-historikken i databasen
                        //  Vi prosesserer alle rettighetsperioder fordi det er mulig at DP er innvilget før søknadsdato
                        //  Vi oppretter VedtakHendelse for hver rettighetsperiode slik at personregister kan vurdere ny status iht til den siste versjonen av sannheten fra PJ
                        //  Dvs. det spiller ingen rolle om det er en ny rettighetsperiode eller en endring. Bare opprett nye VedtakHendelser for alle perioder og la personregister bestemme
                        val fraOgMed = rettighetsperiode["fraOgMed"].asLocalDate()
                        val tilOgMed = rettighetsperiode["tilOgMed"]?.asLocalDate()
                        val harRett = rettighetsperiode["harRett"].asBoolean()

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
