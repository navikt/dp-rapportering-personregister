package no.nav.dagpenger.rapportering.personregister.mediator.db

import no.nav.dagpenger.rapportering.personregister.modell.Hendelse
import no.nav.dagpenger.rapportering.personregister.modell.Person
import java.util.UUID

interface PersonRepository {
    fun hentPerson(ident: String): Person?

    fun finnesPerson(ident: String): Boolean

    fun lagrePerson(person: Person)

    fun oppdaterPerson(person: Person)

    fun oppdaterIdent(
        person: Person,
        nyIdent: String,
    )

    fun hentAntallPersoner(): Int

    fun hentAntallHendelser(): Int

    fun hentAntallFremtidigeHendelser(): Int

    fun hentAntallDagpengebrukere(): Int

    fun hentAntallOvetagelser(): Int

    fun lagreFremtidigHendelse(hendelse: Hendelse)

    fun hentHendelserSomSkalAktiveres(): List<Hendelse>

    fun slettFremtidigHendelse(referanseId: String)

    fun hentPersonerMedDagpenger(): List<String>

    fun hentPersonerMedDagpengerUtenArbeidssokerperiode(): List<String>

    fun hentPersonerSomKanSlettes(): List<String>

    fun slettPerson(ident: String)

    fun hentPersonMedPeriodeId(periodeId: UUID): Person?
}
