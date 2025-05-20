package no.nav.dagpenger.rapportering.personregister.mediator.db

import no.nav.dagpenger.rapportering.personregister.modell.AnnenMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.DagpengerMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.Hendelse
import no.nav.dagpenger.rapportering.personregister.modell.MeldepliktHendelse
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.Status
import no.nav.dagpenger.rapportering.personregister.modell.overtattBekreftelse
import java.time.LocalDateTime

class InMemoryPersonRepository : PersonRepository {
    private val personList = mutableMapOf<String, Person>()
    private val fremtidigeHendelser = mutableListOf<Hendelse>()

    override fun hentPerson(ident: String): Person? = personList[ident]

    override fun finnesPerson(ident: String): Boolean = personList.containsKey(ident)

    override fun lagrePerson(person: Person) {
        personList[person.ident] = person
    }

    override fun oppdaterPerson(person: Person) {
        personList[person.ident] = person
    }

    override fun oppdaterIdent(
        person: Person,
        nyIdent: String,
    ) {
        val person =
            personList[person.ident]
                ?: throw IllegalArgumentException("Person med ident ${person.ident} finnes ikke")
        personList.remove(person.ident)
        personList[nyIdent] = person.copy(ident = nyIdent)
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
}
