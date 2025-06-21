package no.nav.dagpenger.rapportering.personregister.mediator.db

import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.Status
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.AnnenMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.DagpengerMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.Hendelse
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.MeldepliktHendelse
import no.nav.dagpenger.rapportering.personregister.modell.overtattBekreftelse
import java.time.LocalDateTime
import java.util.UUID

class InMemoryPersonRepository : PersonRepository {
    private val personList = mutableMapOf<String, Person>()
    private val personList2 = mutableListOf<Person>()
    private val fremtidigeHendelser = mutableListOf<Hendelse>()

    override fun hentPerson(ident: String): Person? = personList[ident]

    override fun finnesPerson(ident: String): Boolean = personList.containsKey(ident)

    override fun lagrePerson(person: Person) {
        personList[person.ident] = person
        personList2.add(person)
    }

    override fun oppdaterPerson(person: Person) {
        val nyPerson = person.deepCopy(versjon = person.versjon + 1)
        nyPerson.setAnsvarligSystem(person.ansvarligSystem)
        personList[person.ident] = nyPerson
    }

    override fun oppdaterIdent(
        person: Person,
        nyIdent: String,
    ) {
        val funnetPerson =
            personList[person.ident]
                ?: throw IllegalArgumentException("Person med ident ${person.ident} finnes ikke")
        personList.remove(funnetPerson.ident)
        personList[nyIdent] = funnetPerson.copy(ident = nyIdent)
    }

    override fun hentAntallPersoner(): Int = personList.size

    override fun hentAntallHendelser(): Int = personList.values.sumOf { it.hendelser.size }

    override fun hentAntallFremtidigeHendelser(): Int = fremtidigeHendelser.size

    override fun hentAntallDagpengebrukere(): Int = personList.values.filter { it.status == Status.DAGPENGERBRUKER }.size

    override fun hentAntallOvetagelser(): Int = personList.values.filter { it.overtattBekreftelse }.size

    override fun lagreFremtidigHendelse(hendelse: Hendelse) {
        fremtidigeHendelser.add(hendelse)
    }

    override fun hentHendelserSomSkalAktiveres(): List<Hendelse> =
        fremtidigeHendelser.filter {
            val nå = LocalDateTime.now()
            when (it) {
                is MeldepliktHendelse -> it.startDato.isBefore(nå)
                is DagpengerMeldegruppeHendelse -> it.startDato.isBefore(nå)
                is AnnenMeldegruppeHendelse -> it.startDato.isBefore(nå)
                else -> false
            }
        }

    override fun slettFremtidigHendelse(referanseId: String) {
        fremtidigeHendelser.find { it.referanseId == referanseId }?.let {
            fremtidigeHendelser.remove(it)
        }
    }

    override fun hentPersonerMedDagpenger(): List<String> =
        personList.values.filter { it.status == Status.DAGPENGERBRUKER }.map { it.ident }

    override fun hentPersonerMedDagpengerUtenArbeidssokerperiode(): List<String> =
        personList.values
            .filter {
                it.status == Status.DAGPENGERBRUKER && it.arbeidssøkerperioder.isEmpty()
            }.map { it.ident }

    override fun hentPersonerSomKanSlettes(): List<String> =
        personList.values
            .filter { person ->
                person.status == Status.IKKE_DAGPENGERBRUKER && fremtidigeHendelser.find { it.ident == person.ident } == null
            }.map { it.ident }

    override fun slettPerson(ident: String) {
        personList.remove(ident)
    }

    override fun hentPersonMedPeriodeId(periodeId: UUID): Person? =
        personList.values.find { person ->
            person.arbeidssøkerperioder.any { it.periodeId == periodeId }
        }

    override fun hentAlleIdenter(): List<String> = personList2.map { it.ident }

    override fun hentIdenterMedAvvik(): List<String> = emptyList()

    override fun hentPersonId(ident: String): Long = 0

    private fun Person.deepCopy(versjon: Int) =
        Person(
            ident = this.ident,
            statusHistorikk = this.statusHistorikk,
            arbeidssøkerperioder = this.arbeidssøkerperioder,
            versjon = versjon,
        ).also { nyPerson ->
            nyPerson.setMeldegruppe(this.meldegruppe)
            nyPerson.setMeldeplikt(this.meldeplikt)
            nyPerson.hendelser.addAll(this.hendelser)
        }
}
