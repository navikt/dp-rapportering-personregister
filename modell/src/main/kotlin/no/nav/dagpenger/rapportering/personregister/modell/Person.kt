package no.nav.dagpenger.rapportering.personregister.modell

data class Person(
    val ident: String,
    var status: Status = SØKT,
) {
    val hendelser = mutableListOf<Hendelse>()

    fun behandle(hendelse: Hendelse) {
        hendelser.add(hendelse)
        status = status.håndter(hendelse)
    }
}
