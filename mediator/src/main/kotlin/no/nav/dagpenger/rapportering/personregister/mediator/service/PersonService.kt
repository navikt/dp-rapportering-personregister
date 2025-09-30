package no.nav.dagpenger.rapportering.personregister.mediator.service

import com.github.benmanes.caffeine.cache.Cache
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.rapportering.personregister.mediator.connector.MeldekortregisterConnector
import no.nav.dagpenger.rapportering.personregister.mediator.connector.PdlConnector
import no.nav.dagpenger.rapportering.personregister.mediator.db.OptimisticLockingException
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.modell.AnsvarligSystem
import no.nav.dagpenger.rapportering.personregister.modell.Arbeidssøkerperiode
import no.nav.dagpenger.rapportering.personregister.modell.Ident
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.PersonObserver
import no.nav.dagpenger.rapportering.personregister.modell.sendFrasigelsesmelding
import no.nav.dagpenger.rapportering.personregister.modell.vurderNyStatus
import java.util.UUID

class PersonService(
    private val pdlConnector: PdlConnector,
    private val personRepository: PersonRepository,
    private val personObservers: List<PersonObserver>,
    private val cache: Cache<String, List<Ident>>,
    private val meldekortregisterConnector: MeldekortregisterConnector,
) {
    // testing
    fun triggerFrasigelse(periodeList: List<UUID>) {
        logger.info { "Triggerer frasigelse" }

        periodeList.forEach { periodeId ->
            val person = personRepository.hentPersonMedPeriodeId(periodeId)

            if (person != null) {
                logger.info { "Sender frasigelsesmelding for periode $periodeId" }

                if (person.observers.isEmpty()) {
                    personObservers.forEach { observer -> person.addObserver(observer) }
                }
                person.sendFrasigelsesmelding(periodeId, false)
            } else {
                logger.warn { "Fant ikke person med periodeId $periodeId" }
            }
        }
    }

    fun hentArbeidssokerperioder(personId: Long): List<Arbeidssøkerperiode> {
        logger.info { "Henter arbeidssøkerperioder for $personId" }

        val ident = personRepository.hentIdent(personId) ?: throw IllegalArgumentException("Fant ikke person med id $personId")
        val person = hentPerson(ident) ?: throw IllegalArgumentException("Fant ikke person med ident $ident")

        return person.arbeidssøkerperioder
    }

    fun hentPerson(ident: String): Person? {
        logger.info { "Henter person" }
        // Henter alle personens identer fra PDL
        val pdlIdenter = hentAlleIdenterForPerson(ident)
        logger.info { "Personen har ${pdlIdenter.size} identer i PDL" }

        val personer =
            hentPersoner(
                identer =
                    if (pdlIdenter.isEmpty()) {
                        listOf(ident)
                    } else {
                        // Fjerner aktorId siden denne ikke brukes av oss
                        pdlIdenter.filterNot { it.gruppe == Ident.IdentGruppe.AKTORID }.map { it.ident }
                    },
            )
        logger.info { "Personen finnes ${personer.size} ganger i databasen" }
        return ryddOppPersoner(pdlIdenter, personer)
    }

    fun hentPersonId(ident: String): Long? = personRepository.hentPersonId(ident)

    fun hentIdent(personId: Long): String? = personRepository.hentIdent(personId)

    private fun hentAlleIdenterForPerson(ident: String): List<Ident> = cache.get(ident) { pdlConnector.hentIdenter(ident) }

    fun hentPersoner(identer: List<String>): List<Person> =
        identer.mapNotNull { ident ->
            personRepository.hentPerson(ident).also { person ->
                if (person != null && person.observers.isEmpty()) {
                    personObservers.forEach { observer -> person.addObserver(observer) }
                }
            }
        }

    private fun ryddOppPersoner(
        pdlIdentliste: List<Ident>,
        personer: List<Person>,
    ): Person? {
        val gjeldendeIdent = pdlIdentliste.hentGjeldendeIdent()
        if (personer.isEmpty()) {
            logger.info { "Personen finnes ikke databasen." }
            return null
        } else if (personer.size == 1) {
            logger.info { "Personen funnes kun 1 gang i databasen. Sjekker om gjeldende ident må oppdateres." }
            val person = personer.first()
            if (gjeldendeIdent != null && person.ident != gjeldendeIdent) {
                logger.info { "Oppdaterer ident for person" }
                sikkerLogg.info { "Oppdaterer ident fra ${person.ident} til $gjeldendeIdent" }
                personRepository.oppdaterIdent(person, gjeldendeIdent)

                if (person.ansvarligSystem == AnsvarligSystem.DP) {
                    runBlocking {
                        val personId =
                            personRepository.hentPersonId(person.ident)
                                ?: throw IllegalStateException("Person finnes ikke")
                        meldekortregisterConnector.oppdaterIdent(personId, person.ident, gjeldendeIdent)
                    }
                }

                return person.copy(ident = gjeldendeIdent)
            } else {
                return person
            }
        } else {
            if (gjeldendeIdent != null) {
                val gjeldendePerson =
                    personer.firstOrNull { it.ident == gjeldendeIdent } ?: Person(gjeldendeIdent).also { person ->
                        if (person.observers.isEmpty()) {
                            personObservers.forEach { observer -> person.addObserver(observer) }
                        }
                    }
                if (pdlIdentliste.size > 1) {
                    pdlIdentliste.konsoliderPersonerTilGjeldendePerson(gjeldendePerson, personer)
                    konsoliderArbeidssøkerperioderForGjeldendePerson(gjeldendePerson, personer)

                    if (gjeldendePerson.ansvarligSystem == AnsvarligSystem.DP) {
                        runBlocking {
                            val personId =
                                personRepository.hentPersonId(gjeldendePerson.ident)
                                    ?: throw IllegalStateException("Person finnes ikke")
                            meldekortregisterConnector.konsoliderIdenter(
                                personId,
                                gjeldendePerson.ident,
                                personer.map { it.ident },
                            )
                        }
                    }
                }
                gjeldendePerson.setStatus(gjeldendePerson.vurderNyStatus())
                if (personRepository.finnesPerson(gjeldendePerson.ident)) {
                    try {
                        personRepository.oppdaterPerson(gjeldendePerson)
                    } catch (e: OptimisticLockingException) {
                        sikkerLogg.warn(e) { "Optimistisk låsing feilet ved oppdatering av person ${gjeldendePerson.ident}" }
                        ryddOppPersoner(pdlIdentliste, personer)
                    }
                } else {
                    personRepository.lagrePerson(gjeldendePerson)
                }
                return gjeldendePerson
            } else {
                logger.warn { "Fant ingen gjeldende ident for ${personer.map { it.ident }}" }
                return null
            }
        }
    }

    private fun List<Ident>.konsoliderPersonerTilGjeldendePerson(
        gjeldendePerson: Person,
        personer: List<Person>,
    ) {
        this
            .filter { it.ident != gjeldendePerson.ident }
            .forEach { pdlIdent ->
                val historiskPerson = personer.firstOrNull { it.ident == pdlIdent.ident }
                if (historiskPerson != null) {
                    sikkerLogg.info { "Konsoliderer person med ident ${pdlIdent.ident} til ${gjeldendePerson.ident}" }
                    gjeldendePerson.hendelser.addAll(historiskPerson.hendelser)
                    historiskPerson.statusHistorikk.getAll().forEach {
                        gjeldendePerson.statusHistorikk.put(it.first, it.second)
                    }
                    val arbeidssøkerperioder =
                        (gjeldendePerson.arbeidssøkerperioder + (historiskPerson.arbeidssøkerperioder))
                            .map { it.copy(ident = gjeldendePerson.ident) }
                            .toMutableList()

                    gjeldendePerson.arbeidssøkerperioder.clear()
                    gjeldendePerson.arbeidssøkerperioder.addAll(arbeidssøkerperioder.distinctBy { it.periodeId })

                    personRepository.slettPerson(pdlIdent.ident)

                    gjeldendePerson.apply {
                        if (!meldeplikt && historiskPerson.meldeplikt) {
                            setMeldeplikt(true)
                        }
                        if (meldegruppe != "DAGP" && historiskPerson.meldegruppe == "DAGP") {
                            setMeldegruppe(historiskPerson.meldegruppe)
                        }
                    }
                } else {
                    sikkerLogg.info { "Fant ikke historisk person med ident ${pdlIdent.ident}" }
                }
            }
    }

    private fun konsoliderArbeidssøkerperioderForGjeldendePerson(
        gjeldendePerson: Person,
        personer: List<Person>,
    ) {
        val overtatteArbeidssøkerperioder =
            gjeldendePerson.arbeidssøkerperioder.filter {
                it.avsluttet == null && it.overtattBekreftelse == true
            }
        if (overtatteArbeidssøkerperioder.size > 1) {
            val nyesteOvertattePeriode = overtatteArbeidssøkerperioder.maxByOrNull { it.startet }
            sikkerLogg.info { "Nyeste overtatte periode er ${nyesteOvertattePeriode?.periodeId}" }
            val arbeidssøkerperioder =
                gjeldendePerson.arbeidssøkerperioder.map { arbeidssøkerperiode ->
                    if (arbeidssøkerperiode != nyesteOvertattePeriode) {
                        personer
                            .find { arbeidssøkerperiode.ident == it.ident }
                            ?.sendFrasigelsesmelding(
                                arbeidssøkerperiode.periodeId,
                                false,
                            )
                        arbeidssøkerperiode.overtattBekreftelse = false
                        arbeidssøkerperiode.copy(overtattBekreftelse = false)
                    }
                    arbeidssøkerperiode
                }
            sikkerLogg.info { "Konsoliderer arbeidssøkerperioder for ${gjeldendePerson.ident}: $arbeidssøkerperioder" }

            gjeldendePerson.arbeidssøkerperioder.clear()
            gjeldendePerson.arbeidssøkerperioder.addAll(arbeidssøkerperioder.distinctBy { it.periodeId })
        }
    }

    companion object {
        private val logger = KotlinLogging.logger {}
        private val sikkerLogg = KotlinLogging.logger("tjenestekall")
    }
}

private fun List<Ident>.hentGjeldendeIdent(): String? =
    (
        this.firstOrNull { it.gruppe == Ident.IdentGruppe.FOLKEREGISTERIDENT && !it.historisk }
            ?: this.firstOrNull { it.gruppe == Ident.IdentGruppe.NPID && !it.historisk }
    )?.ident
