package no.nav.dagpenger.rapportering.personregister.mediator.service
import com.github.benmanes.caffeine.cache.Cache
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.connector.PdlConnector
import no.nav.dagpenger.rapportering.personregister.mediator.db.OptimisticLockingException
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
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
) {
    // testing
    fun triggerFrasigelse(periodeList: List<UUID>) {
        logger.info { "Triggerer frasigelse" }

        periodeList.forEach { periodeId ->
            val person = personRepository.hentPersonMedPeriodeId(periodeId)

            if (person != null) {
                logger.info("Sender frasigelsesmelding for periode $periodeId")

                if (person.observers.isEmpty()) {
                    personObservers.forEach { observer -> person.addObserver(observer) }
                }
                person.sendFrasigelsesmelding(periodeId, false)
            } else {
                logger.warn("Fant ikke person med periodeId $periodeId")
            }
        }
    }

    fun hentPerson(ident: String): Person? =
        personRepository.hentPerson(ident).also { person ->
            if (person != null && person.observers.isEmpty()) {
                personObservers.forEach { observer -> person.addObserver(observer) }
            }
        }

    private fun hentAlleIdenterForPerson(ident: String): List<Ident> = cache.get(ident) { pdlConnector.hentIdenter(ident) }

    fun hentPersonFraDB(identer: List<String>): List<Person> =
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
            return null
        } else if (personer.size == 1) {
            val person = personer.first()
            if (gjeldendeIdent != null && person.ident != gjeldendeIdent) {
                sikkerLogg.info("Oppdaterer ident fra ${person.ident} til $gjeldendeIdent")
                personRepository.oppdaterIdent(person, gjeldendeIdent)
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
                logger.warn("Fant ingen gjeldende ident for ${personer.map { it.ident }}")
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
                    sikkerLogg.info("Konsoliderer person med ident ${pdlIdent.ident} til ${gjeldendePerson.ident}")
                    gjeldendePerson.hendelser.addAll(historiskPerson?.hendelser ?: emptyList())
                    historiskPerson?.statusHistorikk?.getAll()?.forEach { it ->
                        gjeldendePerson.statusHistorikk.put(it.first, it.second)
                    }
                    val arbeidssøkerperioder =
                        (gjeldendePerson.arbeidssøkerperioder + (historiskPerson?.arbeidssøkerperioder ?: emptyList()))
                            .map { it.copy(ident = gjeldendePerson.ident) }
                            .toMutableList()

                    gjeldendePerson.arbeidssøkerperioder.clear()
                    gjeldendePerson.arbeidssøkerperioder.addAll(arbeidssøkerperioder.distinctBy { it.periodeId })

                    personRepository.slettPerson(pdlIdent.ident)

                    gjeldendePerson.apply {
                        if (!meldeplikt && historiskPerson?.meldeplikt == true) {
                            setMeldeplikt(true)
                        }
                        if (meldegruppe != "DAGP" && historiskPerson?.meldegruppe == "DAGP") {
                            setMeldegruppe(historiskPerson.meldegruppe)
                        }
                    }
                } else {
                    sikkerLogg.info("Fant ikke historisk person med ident ${pdlIdent.ident}")
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
            sikkerLogg.info("Nyeste overtatte periode er ${nyesteOvertattePeriode?.periodeId}")
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
            sikkerLogg.info("Konsoliderer arbeidssøkerperioder for ${gjeldendePerson.ident}: $arbeidssøkerperioder")

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
