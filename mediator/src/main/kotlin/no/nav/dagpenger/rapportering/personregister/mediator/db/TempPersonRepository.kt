package no.nav.dagpenger.rapportering.personregister.mediator.db

enum class TempPersonStatus {
    IKKE_PABEGYNT,
    FERDIGSTILT,
}

data class TempPerson(
    val ident: String,
    var status: TempPersonStatus = TempPersonStatus.IKKE_PABEGYNT,
)

interface TempPersonRepository {
    fun hentPerson(ident: String): TempPerson?

    fun lagrePerson(person: TempPerson)

    fun oppdaterPerson(person: TempPerson): TempPerson?

    fun slettPerson(ident: String)

    fun hentAlleIdenter(): List<String>

    fun isEmpty(): Boolean = hentAlleIdenter().isEmpty()

    fun syncPersoner()
}
