package no.nav.dagpenger.rapportering.personregister.mediator.service

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.oshai.kotlinlogging.withLoggingContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.rapportering.personregister.mediator.api.PersonNotFoundException
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.observers.PersonObserverMeldekortregister
import no.nav.dagpenger.rapportering.personregister.mediator.utils.UUIDv7
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.Hendelse
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.VedtakHendelse
import java.time.LocalDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

private val logger = KotlinLogging.logger {}

class ManuellMigreringAvMeldekortForBrukereSomBareHarAvslagService(
    private val personRepository: PersonRepository,
    private val personObserverMeldekortregister: PersonObserverMeldekortregister,
) {
    fun migrerMeldekort(
        personIder: List<Long>?,
        pauseMellomMigreringer: Duration = 100.milliseconds,
    ) {
        val korrelasjonsId = UUIDv7.newUuid()
        withLoggingContext(
            "korrelasjonsId" to korrelasjonsId.toString(),
        ) {
            logger.info { "START Behandler personIder" }
            personIder?.distinct()?.forEach { personId ->
                runBlocking {
                    try {
                        logger.info { "START Behandler personId $personId" }
                        // hent person
                        val person =
                            personRepository.hentPerson(personId)
                                ?: throw PersonNotFoundException("Fant ikke personId $personId")

                        // verifiser at personen bare har VedtakHendelse med utfall:false, ingen med utfall:true
                        verifiserAtBrukerIkkeHarVedtakHendelseMedUtfallTrue(person, personId)

                        // Finn eldste VedtakHendelse med utfall: false motatt etter 2026-08-31T09:00
                        // (da vi sluttet å skrive avslag tilbake til Arena), og har fraOgMed før 2026-10-16T14:35
                        // (da vi begynte å migrere meldekort ved avlag).
                        val vedtakHendelseMedUtfallFalse =
                            person.hendelser
                                .filter { hendelse ->
                                    erVedtakHendelseMedUtfallFalse(hendelse) &&
                                        erMottattEtterAtDpSakSluttetÅSkriveAvslagTilbakeTilArena(hendelse) &&
                                        erMottattFørDpMeldekortregisterBegynteÅMigrereMeldekortVedAvslag(hendelse)
                                }.minByOrNull { hendelse -> hendelse.dato } as? VedtakHendelse

                        requireNotNull(vedtakHendelseMedUtfallFalse) {
                            "personId $personId har ingen VedtakHendelser med utfall:false mottatt etter at DP-Sak sluttet å skrive avslag tilbake til Arena, og før dp-meldekortregister begynte å migrere meldekort ved avslag."
                        }

                        // Send Meldekortmelding med handling:STOPP og data fra VedtakHendelse
                        logger.info { "Sender Stopp-melding til Meldekortregister for personId $personId" }
                        personObserverMeldekortregister.sendStoppMeldingTilMeldekortregister(
                            person,
                            vedtakHendelseMedUtfallFalse.startDato,
                            null,
                            vedtakHendelseMedUtfallFalse.utfall,
                            korrelasjonsId,
                        )

                        logger.info { "SLUTT Behandler personId $personId" }

                        logger.info { "Venter ${pauseMellomMigreringer.inWholeMilliseconds}ms før vi behandler neste personId." }
                        delay(pauseMellomMigreringer)
                    } catch (e: Exception) {
                        logger.error(e) { "FEILET Behandler personId $personId: ${e.message}" }
                    }
                }
            }
            logger.info { "SLUTT Behandler personIder" }
        }
    }

    private fun erVedtakHendelseMedUtfallFalse(hendelse: Hendelse): Boolean = hendelse is VedtakHendelse && !hendelse.utfall

    private fun erMottattFørDpMeldekortregisterBegynteÅMigrereMeldekortVedAvslag(hendelse: Hendelse): Boolean =
        hendelse.dato.isBefore(LocalDateTime.parse("2026-10-16T14:35:00"))

    private fun erMottattEtterAtDpSakSluttetÅSkriveAvslagTilbakeTilArena(hendelse: Hendelse): Boolean =
        hendelse.dato.isAfter(LocalDateTime.parse("2026-08-31T09:00:00"))

    private fun verifiserAtBrukerIkkeHarVedtakHendelseMedUtfallTrue(
        person: Person,
        personId: Long,
    ) {
        require(
            person.hendelser.none { hendelse ->
                hendelse is VedtakHendelse && hendelse.utfall
            },
        ) {
            "personId $personId har minst 1 VedtakHendelse med utfall:true. Bruker kan bare ha VedtakHendelse med utfall:false. Brukere med utfall:true har allerde fått sine meldekort migrert."
        }
    }
}
