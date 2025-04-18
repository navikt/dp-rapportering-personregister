package no.nav.dagpenger.rapportering.personregister.modell

interface PersonObserver {
    fun overtaArbeidssøkerBekreftelse(person: Person) {}

    fun frasiArbeidssøkerBekreftelse(
        person: Person,
        fristBrutt: Boolean = false,
    )
}
