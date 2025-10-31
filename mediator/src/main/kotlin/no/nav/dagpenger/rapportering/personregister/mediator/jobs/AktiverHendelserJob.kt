package no.nav.dagpenger.rapportering.personregister.mediator.jobs

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.rapportering.personregister.mediator.MeldestatusMediator
import no.nav.dagpenger.rapportering.personregister.mediator.PersonMediator
import no.nav.dagpenger.rapportering.personregister.mediator.connector.MeldepliktConnector
import no.nav.dagpenger.rapportering.personregister.mediator.connector.createHttpClient
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
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
            } else {
                logger.info { "Pod er ikke leader, så jobb for å aktivere fremtidige hendelser startes ikke" }
            }
        } catch (e: Exception) {
            logger.error(e) { "Jobb for å aktivere hendelser mottatt med dato fram i tid feilet" }
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
        var currentIdent = ""
        var currentMeldestatus: MeldestatusResponse? = null
        hendelser.forEach { hendelse ->
            val person = personService.hentPerson(hendelse.ident)
            try {
                if (person != null) {
                    when (hendelse) {
                        is DagpengerMeldegruppeHendelse, is AnnenMeldegruppeHendelse, is MeldepliktHendelse -> {
                            // Sjekk om bruker har meldt seg
                            // Hendelser er sortert etter ident
                            // Da kan vi hente meldestatus for hver ident og ikke for hver hendelse
                            if (hendelse.ident != currentIdent || currentMeldestatus == null) {
                                val meldestatus =
                                    runBlocking {
                                        meldepliktConnector.hentMeldestatus(ident = hendelse.ident)
                                    }

                                // Det er veldig rart at vi ikke kan hente meldestatus her fordi vi fikk data fra Arena, men for sikkerhetsskyld
                                if (meldestatus == null) {
                                    logger.error { "Kunne ikke hente meldestatus" }
                                    sikkerLogg.error { "Kunne ikke hente meldestatus for person med ident ${hendelse.ident}" }
                                    throw RuntimeException("Kunne ikke hente meldestatus")
                                }

                                currentIdent = hendelse.ident
                                currentMeldestatus = meldestatus
                            }

                            meldestatusMediator.behandleHendelse(hendelse.referanseId, person, currentMeldestatus)
                        }

                        is VedtakHendelse -> {
                            personMediator.behandle(hendelse)
                        }

                        else -> {
                            logger.warn { "Fant ukjent fremtidig hendelsetype $hendelse" }
                        }
                    }
                } else {
                    logger.warn { "Fant ikke person. Kan ikke aktivere hendelse." }
                    sikkerLogg.warn { "Fant ikke person med ident ${hendelse.ident}. Kan ikke aktivere hendelse." }
                }
                personRepository.slettFremtidigHendelse(hendelse.referanseId)
            } catch (e: Exception) {
                logger.error(e) { "Aktivering av hendelse med referanseId ${hendelse.referanseId} feilet" }
                sikkerLogg.error(e) { "Aktivering av hendelse med referanseId ${hendelse.referanseId} feilet" }
            }
        }
        return hendelser.size
    }
}
