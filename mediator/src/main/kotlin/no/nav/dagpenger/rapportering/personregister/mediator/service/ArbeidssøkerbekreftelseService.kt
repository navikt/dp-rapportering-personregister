package no.nav.dagpenger.rapportering.personregister.mediator.service

import no.nav.dagpenger.rapportering.personregister.mediator.kafka.ArbeidssøkerbekreftelseProdusent
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.oppfyllerKrav
import no.nav.dagpenger.rapportering.personregister.modell.overtattBekreftelse

class ArbeidssøkerbekreftelseService(
    private val arbeidssøkerbekreftelseProdusent: ArbeidssøkerbekreftelseProdusent,
) {
    fun behandle(
        person: Person,
        fristBrutt: Boolean,
    ) {
        if (person.overtattBekreftelse) {
            if (!person.oppfyllerKrav) {
                arbeidssøkerbekreftelseProdusent.sendFrasigelsesmelding(person, fristBrutt)
            }
            return
        }

        if (person.oppfyllerKrav) {
            arbeidssøkerbekreftelseProdusent.sendOvertakelsesmelding(person)
        }
    }
}
