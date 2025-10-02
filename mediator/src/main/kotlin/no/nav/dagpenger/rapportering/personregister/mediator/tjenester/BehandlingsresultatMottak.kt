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
import no.nav.dagpenger.rapportering.personregister.mediator.db.BehandlingRepository
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.BehandlingsresultatMetrikker
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.VedtakHendelse
import java.time.LocalDate
import java.time.LocalDateTime

class BehandlingsresultatMottak(
    rapidsConnection: RapidsConnection,
    private val personRepository: PersonRepository,
    private val behandlingRepository: BehandlingRepository,
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

            // Her er vi avhengige av at vi får vedtak_fattet_utenfor_arena før behandlingsresultat
            var søknadId = behandlingRepository.hentSøknadIdForBehandlingId(behandlingId)
            if (packet["behandletHendelse"]["type"].asText() == "Søknad") {
                søknadId = packet["behandletHendelse"]["id"].asText()
            }
            if (søknadId == null) {
                logger.error { "Kan ikke finne søknadId for behandlingId $behandlingId" }
                throw Exception("Kan ikke finne søknadId for behandlingId $behandlingId")
            }

            val ident = packet["ident"].asText()

            if (!ident.matches(Regex("[0-9]{11}"))) {
                logger.error { "Person-ident må ha 11 sifre" }
                return
            }

            // Slett fremtidige VedtakHendelser
            personRepository.slettFremtidigeVedtakHendelser(ident)

            packet["rettighetsperioder"].toList().forEachIndexed { index, rettighetsperiode ->
                // TODO:
                //  Må vi filtrere rettighetsperiode her? Må vi prosessere passerte perioder?
                //  Kan vi ignorere perioder med tilOgMed før søknadsdato? Eller bruker kan få innvilget DP med tilOgMed før søknadsdato?
                //  Må vi sjekke at vi allerede har et vedtak for denne perioden? Må vi slette eller oppdatere det? Eller opprette et nytt vedtak? Eller gjøre ingenting?
                //  _
                //  Jeg synes at vi ikke må oppdatere eller slette eksisterende vedtak. Vi må bare opprette nye. Da kan vi ha hele vedtak-historikken i databasen
                //  Som jeg forstår må vi prosessere alle rettighetsperioder fordi det er mulig at DP er innvilget før søknadsdato
                //  Jeg tror vi kan opprette vedtak for hver rettighetsperiode slik at personregister kan vurdere ny status iht til den siste versjonen av sannheten fra PJ
                //  Dvs. det spiller ingen rolle om det er en ny rettighetsperiode eller noe endring. Bare opprett nye vedtak for alle perioder og la personregister bestemme
                //  Vi skal sende alt dette til dp-meldekortregister slik at meldekortregister kan bestemme om det er nødvendig å opprette meldekort eller ikke
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
                        søknadId = søknadId,
                        utfall = harRett,
                    )

                if (fraOgMed.isAfter(LocalDate.now())) {
                    fremtidigHendelseMediator.behandle(vedtakHendelse)
                } else {
                    personMediator.behandle(vedtakHendelse)
                }
            }
        }
    }
}
