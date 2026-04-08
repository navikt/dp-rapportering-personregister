package no.nav.dagpenger.rapportering.personregister.mediator.service

import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import no.nav.dagpenger.rapportering.personregister.mediator.ArbeidssøkerMediator
import no.nav.dagpenger.rapportering.personregister.mediator.db.OptimisticLockingException
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.utils.MetrikkerTestUtil.actionTimer
import no.nav.dagpenger.rapportering.personregister.mediator.utils.UUIDv7
import no.nav.dagpenger.rapportering.personregister.modell.AnsvarligSystem
import no.nav.dagpenger.rapportering.personregister.modell.Arbeidssøkerperiode
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.PersonObserver
import no.nav.dagpenger.rapportering.personregister.modell.Status
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.SøknadHendelse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class SøknadServiceTest {
    private val personService = mockk<PersonService>()
    private val personRepository = mockk<PersonRepository>()
    private val arbeidssøkerMediator = mockk<ArbeidssøkerMediator>(relaxed = true)
    private val personObserver = mockk<PersonObserver>(relaxed = true)

    private val søknadService =
        SøknadService(
            personService = personService,
            personRepository = personRepository,
            arbeidssøkerMediator = arbeidssøkerMediator,
            actionTimer = actionTimer,
        )

    private val ident = "12345678901"
    private val dato = LocalDateTime.now()

    @BeforeEach
    fun setup() {
        justRun { personRepository.oppdaterPerson(any()) }
    }

    @Test
    fun `behandler søknad for person i ARENA-regime uten aktiv arbeidssøkerperiode`() {
        val søknadHendelse = lagSøknadHendelse()
        val person = lagPerson(AnsvarligSystem.ARENA)
        every { personService.hentEllerOpprettPerson(ident) } returns person

        søknadService.behandle(søknadHendelse)

        person.hendelser.size shouldBe 1
        person.hendelser.first().referanseId shouldBe søknadHendelse.referanseId
        person.harRettTilDp shouldBe false
        person.status shouldBe Status.IKKE_DAGPENGERBRUKER
        verify(exactly = 1) { personRepository.oppdaterPerson(person) }
        verify(exactly = 1) { arbeidssøkerMediator.behandle(ident) }
    }

    @Test
    fun `behandler søknad for person i DP-regime som er arbeidssøker - setter harRettTilDp og sender startmelding`() {
        val søknadHendelse = lagSøknadHendelse()
        val person = lagPersonMedAktivArbeidssøkerperiode(AnsvarligSystem.DP)
        person.addObserver(personObserver)
        every { personService.hentEllerOpprettPerson(ident) } returns person

        søknadService.behandle(søknadHendelse)

        person.harRettTilDp shouldBe true
        verify(exactly = 1) { personObserver.sendStartMeldingTilMeldekortregister(person, søknadHendelse.startDato, null, false) }
        verify(exactly = 1) { personRepository.oppdaterPerson(person) }
        verify(exactly = 1) { arbeidssøkerMediator.behandle(ident) }
    }

    @Test
    fun `behandler søknad for person i DP-regime som er arbeidssøker - endrer status til DAGPENGERBRUKER og sender overtakelsesmelding`() {
        val søknadHendelse = lagSøknadHendelse()
        val person = lagPersonMedAktivArbeidssøkerperiode(AnsvarligSystem.DP)
        person.addObserver(personObserver)
        every { personService.hentEllerOpprettPerson(ident) } returns person

        søknadService.behandle(søknadHendelse)

        person.status shouldBe Status.DAGPENGERBRUKER
        verify(exactly = 1) { personObserver.sendOvertakelsesmelding(person) }
    }

    @Test
    fun `hopper over søknad som allerede er behandlet (duplikat referanseId)`() {
        val søknadHendelse = lagSøknadHendelse()
        val person = lagPerson(AnsvarligSystem.ARENA)
        person.hendelser.add(søknadHendelse)
        every { personService.hentEllerOpprettPerson(ident) } returns person

        søknadService.behandle(søknadHendelse)

        person.hendelser.size shouldBe 1
        verify(exactly = 0) { personRepository.oppdaterPerson(any()) }
        verify(exactly = 0) { arbeidssøkerMediator.behandle(person.ident) }
    }

    private fun lagSøknadHendelse() =
        SøknadHendelse(
            ident = ident,
            dato = dato,
            startDato = dato,
            referanseId = UUIDv7.newUuid().toString(),
        )

    private fun lagPerson(ansvarligSystem: AnsvarligSystem): Person {
        val person = Person(ident)
        person.setAnsvarligSystem(ansvarligSystem)
        return person
    }

    private fun lagPersonMedAktivArbeidssøkerperiode(ansvarligSystem: AnsvarligSystem): Person {
        val person = lagPerson(ansvarligSystem)
        person.arbeidssøkerperioder.add(
            Arbeidssøkerperiode(
                periodeId = UUID.randomUUID(),
                ident = ident,
                startet = dato.minusDays(1),
                avsluttet = null,
                overtattBekreftelse = null,
            ),
        )
        return person
    }
}
