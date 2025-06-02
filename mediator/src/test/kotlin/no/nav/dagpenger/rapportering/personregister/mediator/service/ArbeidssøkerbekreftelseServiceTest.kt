package no.nav.dagpenger.rapportering.personregister.mediator.service

import io.mockk.mockk
import io.mockk.verify
import no.nav.dagpenger.rapportering.personregister.mediator.db.InMemoryPersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.kafka.ArbeidssøkerbekreftelseProdusent
import no.nav.dagpenger.rapportering.personregister.modell.Arbeidssøkerperiode
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.Status.DAGPENGERBRUKER
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class ArbeidssøkerbekreftelseServiceTest {
    private val personRepository: PersonRepository = InMemoryPersonRepository()
    private val arbeidssøkerbekreftelseProdusent: ArbeidssøkerbekreftelseProdusent = mockk<ArbeidssøkerbekreftelseProdusent>(relaxed = true)
    private val arbeidssøkerbekreftelseService = ArbeidssøkerbekreftelseService(arbeidssøkerbekreftelseProdusent)

    @Test
    fun `behandler person som vi allerede overtatt bekreftelse og oppfyller krav`() {
        val person =
            arbeidssøker(overtattBekreftelse = true) {
                setMeldegruppe("DAGP")
                setMeldeplikt(true)
                setStatus(DAGPENGERBRUKER)
            }

        personRepository.lagrePerson(person)

        arbeidssøkerbekreftelseService.behandle(person, fristBrutt = false)
        verify(exactly = 0) { arbeidssøkerbekreftelseProdusent.sendOvertakelsesmelding(person) }
    }

    @Test
    fun `behandler person som vi allerede overtatt bekreftelse og ikke lenger oppfyller krav`() {
        val person = arbeidssøker(overtattBekreftelse = true) { setMeldeplikt(false) }
        personRepository.lagrePerson(person)

        arbeidssøkerbekreftelseService.behandle(person, false)
        verify(exactly = 1) { arbeidssøkerbekreftelseProdusent.sendFrasigelsesmelding(person = person, fristBrutt = false) }
    }

    @Test
    fun `behandler person som vi ikke overtatt bekreftelse og oppfyller krav`() {
        val person =
            arbeidssøker(overtattBekreftelse = false) {
                setMeldegruppe("DAGP")
                setMeldeplikt(true)
                setStatus(DAGPENGERBRUKER)
            }
        personRepository.lagrePerson(person)

        arbeidssøkerbekreftelseService.behandle(person, false)
        verify(exactly = 1) { arbeidssøkerbekreftelseProdusent.sendOvertakelsesmelding(person = person) }
    }

    @Test
    fun `behandler person som vi ikke overtatt bekreftelse og ikke oppfyller krav`() {
        val person = arbeidssøker(overtattBekreftelse = false) { setMeldeplikt(false) }
        personRepository.lagrePerson(person)

        arbeidssøkerbekreftelseService.behandle(person, false)
        verify(exactly = 0) { arbeidssøkerbekreftelseProdusent.sendFrasigelsesmelding(any(), any()) }
    }

    private fun arbeidssøker(
        overtattBekreftelse: Boolean = false,
        block: Person.() -> Unit,
    ) = Person("12345678901")
        .apply {
            arbeidssøkerperioder.add(
                Arbeidssøkerperiode(
                    UUID.randomUUID(),
                    ident,
                    LocalDateTime.now(),
                    null,
                    overtattBekreftelse = overtattBekreftelse,
                ),
            )
        }.apply(block)
}
