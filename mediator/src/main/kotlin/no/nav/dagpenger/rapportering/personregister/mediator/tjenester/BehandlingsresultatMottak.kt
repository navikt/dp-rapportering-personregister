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
import no.nav.dagpenger.rapportering.personregister.mediator.utils.validerIdent
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.VedtakHendelse
import no.nav.dagpenger.rapportering.personregister.modell.utils.erFortidEllerIdag
import no.nav.dagpenger.rapportering.personregister.modell.utils.erIdagEllerIFremtid
import java.time.LocalDate
import java.time.LocalDateTime.now

private val logger = KotlinLogging.logger {}
private val sikkerlogg = KotlinLogging.logger("tjenestekall")

private const val OPPRINNELSE_PÅ_RETTIGHETSPERIODER_SOM_ER_BEHANDLET_TIDLIGERE = "Arvet"

class BehandlingsresultatMottak(
    rapidsConnection: RapidsConnection,
    private val personRepository: PersonRepository,
    private val personMediator: PersonMediator,
    private val fremtidigHendelseMediator: FremtidigHendelseMediator,
    private val behandlingsresultatMetrikker: BehandlingsresultatMetrikker,
) : River.PacketListener {
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
                        "behandlingskjedeId",
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
        val behandlingskjedeId = packet["behandlingskjedeId"].asText()
        val ident = packet["ident"].asText()

        withLoggingContext(
            "behandlingId" to behandlingId,
            "behandlingskjedeId" to behandlingskjedeId,
            "event_name" to "behandlingsresultat",
        ) {
            logger.info { "Mottok behandlingsresultat-melding" }
            sikkerlogg.info {
                "Mottok behandlingsresultat-melding, ident=$ident (melding logges på neste linje. Er den ikke der? Se https://nav-it.slack.com/docs/T5LNAMWNA/F0B9N9UB7S5)"
            }
            sikkerlogg.info { "Mottok behandlingsresultat-melding, ident=$ident: ${packet.toJson()}" }
            behandlingsresultatMetrikker.behandlingsresultatMottatt.increment()

            try {
                ident.validerIdent()

                personRepository.slettFremtidigeVedtakHendelser(ident)

                packet["rettighetsperioder"]
                    .toList()
                    .sortedBy { it["fraOgMed"].asLocalDate() }
                    .forEachIndexed { index, rettighetsperiode ->
                        val opprinnelse = rettighetsperiode["opprinnelse"].asText()
                        val fraOgMed = rettighetsperiode["fraOgMed"].asLocalDate()
                        val tilOgMed = rettighetsperiode["tilOgMed"]?.asLocalDate()
                        val harRett = rettighetsperiode["harRett"].asBoolean()

                        if (rettighetsperiodenSkalBehandlesNå(opprinnelse, fraOgMed)) {
                            logger.info {
                                "Rettighetsperiode behandles nå: fraOgMed=$fraOgMed, tilOgMed=$tilOgMed, harRett=$harRett"
                            }
                            val vedtakHendelse =
                                VedtakHendelse(
                                    ident = ident,
                                    dato = now(),
                                    startDato = fraOgMed.atStartOfDay(),
                                    sluttDato = tilOgMed?.atStartOfDay(),
                                    referanseId = "$behandlingId-$index",
                                    utfall = harRett,
                                )
                            personMediator.behandle(vedtakHendelse)
                        }

                        if (rettighetsperiodenSkalBehandlesSomFremtidigStart(fraOgMed)) {
                            logger.info {
                                "Rettighetsperiode med fremtidig start legges inn i fremtidig_hendelse: fraOgMed=$fraOgMed, tilOgMed=$tilOgMed, harRett=$harRett"
                            }
                            fremtidigHendelseMediator.behandle(
                                VedtakHendelse.medFremtidigStart(
                                    ident = ident,
                                    startDato = fraOgMed.atStartOfDay(),
                                    sluttDato = tilOgMed?.atStartOfDay(),
                                    referanseId = "$behandlingId-$index",
                                    utfall = harRett,
                                ),
                            )
                        }
                        if (tilOgMed != null && rettighetsperiodenSkalBehandlesSomFremtidigStans(tilOgMed)) {
                            logger.info {
                                "Rettighetsperiode med fremtidig stans legges inn i fremtidig_hendelse: fraOgMed=$fraOgMed, tilOgMed=$tilOgMed, harRett=$harRett"
                            }
                            fremtidigHendelseMediator.behandle(
                                VedtakHendelse.medFremtidigStans(
                                    ident = ident,
                                    startDato = fraOgMed.atStartOfDay(),
                                    sluttDato = tilOgMed.atStartOfDay(),
                                    referanseId = "$behandlingId-$index",
                                    utfall = harRett,
                                ),
                            )
                        }
                    }
            } catch (e: Exception) {
                logger.error(e) { "Feil ved behandling av behandlingsresultat" }
                sikkerlogg.error(e) { "Feil ved behandling av behandlingsresultat, ident=$ident. Selve meldingen er logget tidligere." }
                behandlingsresultatMetrikker.behandlingsresultatFeilet.increment()
                throw e
            }
        }
    }

    private fun rettighetsperiodenSkalBehandlesNå(
        opprinnelse: String,
        fraOgMed: LocalDate,
    ): Boolean = opprinnelse != OPPRINNELSE_PÅ_RETTIGHETSPERIODER_SOM_ER_BEHANDLET_TIDLIGERE && fraOgMed.erFortidEllerIdag()

    private fun rettighetsperiodenSkalBehandlesSomFremtidigStart(fraOgMed: LocalDate): Boolean = !fraOgMed.erFortidEllerIdag()

    private fun rettighetsperiodenSkalBehandlesSomFremtidigStans(tilOgMed: LocalDate): Boolean = tilOgMed.erIdagEllerIFremtid()
}
