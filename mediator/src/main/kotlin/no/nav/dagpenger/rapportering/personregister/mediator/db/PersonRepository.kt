package no.nav.dagpenger.rapportering.personregister.mediator.db

import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.Hendelse
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

    fun slettFremtidigeArenaHendelser(ident: String)

    fun hentPersonerMedDagpenger(): List<String>

    fun hentPersonerMedDagpengerOgAktivPerioode(): List<String> = emptyList()

    fun hentPersonerMedDagpengerMedAvvikBekreftelse(): List<String> = emptyList<String>()

    fun hentPersonerUtenDagpengerMedAvvikBekreftelse(): List<String> = emptyList<String>()

    fun hentPersonerMedDagpengerUtenArbeidssokerperiode(): List<String>

    fun hentPersonerSomKanSlettes(): List<String>

    fun slettPerson(ident: String)

    fun hentPersonMedPeriodeId(periodeId: UUID): Person?

    fun hentAlleIdenter(): List<String>

    fun hentIdenterMedAvvik(): List<String>

    fun hentPersonId(ident: String): Long?

    fun hentIdent(personId: Long): String?
}
