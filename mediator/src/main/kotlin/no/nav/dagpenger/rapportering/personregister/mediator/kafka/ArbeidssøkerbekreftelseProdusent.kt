package no.nav.dagpenger.rapportering.personregister.mediator.kafka

import no.nav.dagpenger.rapportering.personregister.modell.Person

interface ArbeidssøkerbekreftelseProdusent {
    fun sendOvertakelsesmelding(person: Person)

    fun sendFrasigelsesmelding(
        person: Person,
        fristBrutt: Boolean,
    )
}
