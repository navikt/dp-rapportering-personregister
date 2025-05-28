package no.nav.dagpenger.rapportering.personregister.modell

import java.util.UUID

interface PersonObserver {
    fun sendOvertakelsesmelding(person: Person) {}

    fun sendFrasigelsesmelding(
        person: Person,
        fristBrutt: Boolean = false,
    ) {}

    fun overtattArbeidssøkerbekreftelse(
        person: Person,
        periodeId: UUID,
    ) {}

    fun frasagtArbeidssøkerbekreftelse(
        person: Person,
        periodeId: UUID,
    )
}
