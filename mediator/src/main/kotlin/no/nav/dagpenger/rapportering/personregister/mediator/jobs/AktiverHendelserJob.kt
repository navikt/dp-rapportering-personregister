package no.nav.dagpenger.rapportering.personregister.mediator.jobs

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.rapportering.personregister.mediator.MeldestatusMediator
import no.nav.dagpenger.rapportering.personregister.mediator.PersonMediator
import no.nav.dagpenger.rapportering.personregister.mediator.connector.MeldepliktConnector
import no.nav.dagpenger.rapportering.personregister.mediator.connector.createHttpClient
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.JobbkjøringMetrikker
import no.nav.dagpenger.rapportering.personregister.mediator.service.PersonService
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.AnnenMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.DagpengerMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.MeldepliktHendelse
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.VedtakHendelse
import no.nav.dagpenger.rapportering.personregister.modell.meldestatus.MeldestatusResponse
import kotlin.time.measureTime

private val logger = KotlinLogging.logger {}
private val sikkerLogg = KotlinLogging.logger("tjenestekall")

internal class AktiverHendelserJob(
    private val personRepository: PersonRepository,
    private val personService: PersonService,
    private val personMediator: PersonMediator,
    private val meldestatusMediator: MeldestatusMediator,
    private val meldepliktConnector: MeldepliktConnector,
    private val httpClient: HttpClient = createHttpClient(),
    private val jobbkjøringMetrikker: JobbkjøringMetrikker,
) : Task {
    override fun execute() {
        try {
            if (isLeader(httpClient, logger)) {
                logger.info { "Starter jobb for å aktivere hendelser vi mottok med dato fram i tid" }

                var antallHendelser: Int
                val tidBrukt =
                    measureTime {
                        antallHendelser =
                            aktivererHendelser(
                                personRepository,
                                personService,
                                personMediator,
                                meldestatusMediator,
                                meldepliktConnector,
                            )
                    }

                logger.info {
                    "Jobb for å aktivere hendelser vi mottok med dato fram i tid ferdig. " +
                        "Aktiverte $antallHendelser på ${tidBrukt.inWholeSeconds} sekund(er)."
                }
                jobbkjøringMetrikker.jobbFullfort(tidBrukt, antallHendelser)
            } else {
                logger.info { "Pod er ikke leader, så jobb for å aktivere fremtidige hendelser startes ikke" }
            }
        } catch (e: Exception) {
            logger.error(e) { "Jobb for å aktivere hendelser mottatt med dato fram i tid feilet" }
            jobbkjøringMetrikker.jobbFeilet()
        } finally {
            jobbkjøringMetrikker.jobbSjekketOmDenSkulleKjøre()
        }
    }

    private fun aktivererHendelser(
        personRepository: PersonRepository,
        personService: PersonService,
        personMediator: PersonMediator,
        meldestatusMediator: MeldestatusMediator,
        meldepliktConnector: MeldepliktConnector,
    ): Int {
        val hendelser = personRepository.hentHendelserSomSkalAktiveres()
        hendelser.groupBy { hendelse -> hendelse.ident }.forEach { (ident, hendelserForIdent) ->
            var referanseIdForHendelseSomBehandles: String? = null
            try {
                var meldestatus: MeldestatusResponse? = null
                hendelserForIdent.forEach { hendelse ->
                    referanseIdForHendelseSomBehandles = hendelse.referanseId
                    val person = personService.hentPerson(ident)

                    if (person != null) {
                        when (hendelse) {
                            is DagpengerMeldegruppeHendelse, is AnnenMeldegruppeHendelse, is MeldepliktHendelse -> {
                                if (meldestatus == null) {
                                    meldestatus =
                                        runBlocking {
                                            meldepliktConnector.hentMeldestatus(ident = hendelse.ident)
                                        }

                                    // Det er veldig rart at vi ikke kan hente meldestatus her fordi vi fikk data fra Arena, men for sikkerhetsskyld
                                    if (meldestatus == null) {
                                        logger.error { "Kunne ikke hente meldestatus" }
                                        sikkerLogg.error { "Kunne ikke hente meldestatus for person med ident ${hendelse.ident}" }
                                        throw RuntimeException("Kunne ikke hente meldestatus")
                                    }
                                }

                                meldestatusMediator.behandleHendelse(hendelse.referanseId, person, meldestatus)
                            }

                            is VedtakHendelse -> {
                                personMediator.behandle(hendelse)
                            }

                            else -> {
                                throw RuntimeException(
                                    "Ukjent fremtidig hendelsetype ${hendelse::class.simpleName} for hendelse med referanseId=${hendelse.referanseId}",
                                )
                            }
                        }
                        personRepository.slettFremtidigHendelse(hendelse.referanseId)
                        logger.info { "Behandlet hendelse med referanseId=${hendelse.referanseId}. Fremtidig hendelse er slettet." }
                    } else {
                        logger.warn {
                            "Fant ikke person. Hendelsen med referanseId=${hendelse.referanseId} ignoreres og slettes fra fremtidige hendelser."
                        }
                        sikkerLogg.warn {
                            "Fant ikke person med ident=$ident. Hendelsen med referanseId=${hendelse.referanseId} ignoreres og slettes fra fremtidige hendelser."
                        }
                        personRepository.slettFremtidigHendelse(hendelse.referanseId)
                    }
                }
            } catch (e: Exception) {
                val loggmelding =
                    if (referanseIdForHendelseSomBehandles == null) {
                        "Ingen hendelser ble behandlet for personen. Ubehandlede hendelser=[${hendelserForIdent.joinToString {
                            it.referanseId
                        }}]."
                    } else {
                        val ubehandledeHendelser = hendelserForIdent.dropWhile { it.referanseId != referanseIdForHendelseSomBehandles }
                        "Feilende hendelse=$referanseIdForHendelseSomBehandles. Ubehandlede hendelser=[${ubehandledeHendelser.joinToString {
                            it.referanseId
                        }}]."
                    }

                logger.error(e) {
                    "Aktivering av hendelser feilet for person. $loggmelding"
                }
                sikkerLogg.error(e) {
                    "Aktivering av hendelser feilet for person med ident $ident. $loggmelding"
                }
            }
        }
        return hendelser.size
    }
}
