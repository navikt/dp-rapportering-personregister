package no.nav.dagpenger.rapportering.personregister.modell

data class Person(
    val ident: String,
    var status: Status = SØKT,
) {
    val statusHistorikk = TemporalCollection<Status>()

    val hendelser = mutableListOf<Hendelse>()

    fun behandle(hendelse: Hendelse) {
        hendelser.add(hendelse)
        status
            .håndter(hendelse)
            .takeIf { it != status }
            ?.let {
                status = it
                statusHistorikk.put(hendelse.dato, it)
            }.also { hendelser.add(hendelse) }
    }
}
