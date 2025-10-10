package no.nav.dagpenger.rapportering.personregister.mediator

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.rapportering.personregister.mediator.connector.MeldepliktConnector
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.ActionTimer
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.MeldegruppeendringMetrikker
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.MeldepliktendringMetrikker
import no.nav.dagpenger.rapportering.personregister.mediator.service.PersonService
import no.nav.dagpenger.rapportering.personregister.modell.AnsvarligSystem
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.AnnenMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.DagpengerMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.MeldepliktHendelse
import no.nav.dagpenger.rapportering.personregister.modell.meldestatus.MeldestatusHendelse
import no.nav.dagpenger.rapportering.personregister.modell.meldestatus.MeldestatusResponse
import java.time.LocalDateTime

class MeldestatusMediator(
    private val personRepository: PersonRepository,
    private val personService: PersonService,
    private val meldepliktConnector: MeldepliktConnector,
    private val meldepliktMediator: MeldepliktMediator,
    private val personMediator: PersonMediator,
    private val fremtidigHendelseMediator: FremtidigHendelseMediator,
    private val meldepliktendringMetrikker: MeldepliktendringMetrikker,
    private val meldegruppeendringMetrikker: MeldegruppeendringMetrikker,
    private val actionTimer: ActionTimer,
) {
    fun behandle(hendelse: MeldestatusHendelse) =
        actionTimer.timedAction("behandle_MeldestatusHendelse") {
            logger.info { "Behandler meldestatusHendelse med meldestatusId: ${hendelse.meldestatusId}" }

            val meldestatus =
                runBlocking {
                    meldepliktConnector.hentMeldestatus(hendelse.personId)
                }

            // Det er veldig rart at vi ikke kan hente meldestatus her fordi vi får data fra Arena, men for sikkerhetsskyld
            if (meldestatus == null) {
                logger.error { "Kunne ikke hente meldestatus" }
                sikkerLogg.error { "Kunne ikke hente meldestatus for person med personId ${hendelse.personId}" }
                throw RuntimeException("Kunne ikke hente meldestatus")
            }

            personService.hentPerson(meldestatus.personIdent)?.let { person ->
                if (person.ansvarligSystem == AnsvarligSystem.ARENA) {
                    behandleHendelse(hendelse.meldestatusId.toString(), person, meldestatus)
                } else {
                    logger.info { "Person har ikke Arena som ansvarlig system, meldepliktendringer fra Meldestatus ignoreres." }
                    sikkerLogg.info {
                        "Person med ident ${person.ident} har ikke Arena som ansvarlig system, meldepliktendringer fra Meldestatus ignoreres."
                    }
                }
            }
        }

    fun behandleHendelse(
        meldestatusId: String,
        person: Person,
        meldestatus: MeldestatusResponse,
    ) {
        // Meldestatus inneholder:
        // En liste over aktiv(e) meldepliktperiode(r) basert på det som gjelder på søkedato og framover i tid
        // En liste over aktiv(e) meldegruppeperiode(r) basert på det som gjelder på søkedato og framover i tid
        // Hvis ingen dato er angitt, vil dagens dato blir brukt
        // Dvs. vi har lister fra i dag og framover

        // Vi sletter eksisterende fremtidige Arena-hendelser i tilfelle vi får endringer FØR fremtidige hendelsene blir prosessert
        // Slik har vi kun den siste versjonen av sannheten
        personRepository.slettFremtidigeArenaHendelser(person.ident)

        val meldepliktListe =
            meldestatus.meldepliktListe?.sortedBy { it.meldepliktperiode?.fom } ?: emptyList()
        val meldegruppeListe =
            meldestatus.meldegruppeListe?.sortedBy { it.meldegruppeperiode?.fom } ?: emptyList()

        var meldeplikt = person.meldeplikt
        meldepliktListe.forEachIndexed { index, it ->
            if (meldeplikt != it.meldeplikt) {
                val meldepliktHendelse =
                    MeldepliktHendelse(
                        ident = person.ident,
                        referanseId = "MSMP$meldestatusId-$index",
                        dato = LocalDateTime.now(),
                        startDato = it.meldepliktperiode?.fom ?: LocalDateTime.now(),
                        sluttDato = it.meldepliktperiode?.tom,
                        statusMeldeplikt = it.meldeplikt,
                        harMeldtSeg = meldestatus.harMeldtSeg,
                    )

                if (meldepliktHendelse.startDato.isAfter(LocalDateTime.now())) {
                    meldepliktendringMetrikker.fremtidigMeldepliktendringMottatt.increment()
                    fremtidigHendelseMediator.behandle(meldepliktHendelse)
                } else {
                    meldepliktendringMetrikker.meldepliktendringMottatt.increment()
                    meldepliktMediator.behandle(meldepliktHendelse)
                }

                meldeplikt = it.meldeplikt
            }
        }

        var meldegruppe = person.meldegruppe
        meldegruppeListe.forEachIndexed { index, it ->
            if (meldegruppe != it.meldegruppe) {
                if (it.meldegruppe == "DAGP") {
                    val dagpengerMeldegruppeHendelse =
                        DagpengerMeldegruppeHendelse(
                            ident = person.ident,
                            referanseId = "MSMG$meldestatusId-$index",
                            dato = LocalDateTime.now(),
                            startDato = it.meldegruppeperiode?.fom ?: LocalDateTime.now(),
                            sluttDato = it.meldegruppeperiode?.tom,
                            meldegruppeKode = it.meldegruppe,
                            harMeldtSeg = meldestatus.harMeldtSeg,
                        )

                    if (dagpengerMeldegruppeHendelse.startDato.isAfter(LocalDateTime.now())) {
                        meldegruppeendringMetrikker.fremtidigMeldegruppeMottatt.increment()
                        fremtidigHendelseMediator.behandle(dagpengerMeldegruppeHendelse)
                    } else {
                        meldegruppeendringMetrikker.dagpengerMeldegruppeMottatt.increment()
                        personMediator.behandle(dagpengerMeldegruppeHendelse)
                    }
                } else {
                    val annenMeldegruppeHendelse =
                        AnnenMeldegruppeHendelse(
                            ident = person.ident,
                            referanseId = "MSMG$meldestatusId-$index",
                            dato = LocalDateTime.now(),
                            startDato = it.meldegruppeperiode?.fom ?: LocalDateTime.now(),
                            sluttDato = it.meldegruppeperiode?.tom,
                            meldegruppeKode = it.meldegruppe,
                            harMeldtSeg = meldestatus.harMeldtSeg,
                        )

                    if (annenMeldegruppeHendelse.startDato.isAfter(LocalDateTime.now())) {
                        meldegruppeendringMetrikker.fremtidigMeldegruppeMottatt.increment()
                        fremtidigHendelseMediator.behandle(annenMeldegruppeHendelse)
                    } else {
                        meldegruppeendringMetrikker.annenMeldegruppeMottatt.increment()
                        personMediator.behandle(annenMeldegruppeHendelse)
                    }
                }

                meldegruppe = it.meldegruppe
            }
        }
    }

    companion object {
        private val logger = KotlinLogging.logger {}
        private val sikkerLogg = KotlinLogging.logger("tjenestekall")
    }
}
