package no.nav.dagpenger.rapportering.personregister.mediator.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import no.nav.dagpenger.rapportering.personregister.mediator.ArbeidssøkerMediator
import no.nav.dagpenger.rapportering.personregister.mediator.api.PersonNotFoundException
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.utils.MetrikkerTestUtil.actionTimer
import no.nav.dagpenger.rapportering.personregister.mediator.utils.UUIDv7.newUuid
import no.nav.dagpenger.rapportering.personregister.modell.AnsvarligSystem
import no.nav.dagpenger.rapportering.personregister.modell.AnsvarligSystem.ARENA
import no.nav.dagpenger.rapportering.personregister.modell.AnsvarligSystem.DP
import no.nav.dagpenger.rapportering.personregister.modell.Arbeidssøkerperiode
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.PersonObserver
import no.nav.dagpenger.rapportering.personregister.modell.Status
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.SøknadHendelse
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.VedtakHendelse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class SøknadServiceTest {
    private val personService = mockk<PersonService>(relaxed = true)
    private val personRepository = mockk<PersonRepository>()
    private val arbeidssøkerMediator = mockk<ArbeidssøkerMediator>(relaxed = true)
    private val personObserver = mockk<PersonObserver>(relaxed = true)

    private val søknadService =
        SøknadService(
            personService = personService,
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
        val person = lagPerson(ARENA)
        every { personService.hentEllerOpprettPerson(ident) } returns person

        søknadService.behandle(søknadHendelse)

        person.hendelser.size shouldBe 1
        person.hendelser.first().referanseId shouldBe søknadHendelse.referanseId
        person.harRettTilDp shouldBe false
        person.status shouldBe Status.IKKE_DAGPENGERBRUKER
        verify(exactly = 1) { personService.oppdaterPerson(person) }
        verify(exactly = 1) { arbeidssøkerMediator.behandle(ident) }
    }

    @Test
    fun `behandler søknad for person i DP-regime som er arbeidssøker - setter harRettTilDp og sender startmelding`() {
        val søknadHendelse = lagSøknadHendelse()
        val person = lagPersonMedAktivArbeidssøkerperiode()
        person.addObserver(personObserver)
        every { personService.hentEllerOpprettPerson(ident) } returns person

        søknadService.behandle(søknadHendelse)

        person.harRettTilDp shouldBe true
        verify(exactly = 1) {
            personObserver.sendStartMeldingTilMeldekortregister(
                person,
                søknadHendelse.startDato,
                null,
                false,
            )
        }
        verify(exactly = 1) { personService.oppdaterPerson(person) }
        verify(exactly = 1) { arbeidssøkerMediator.behandle(ident) }
    }

    @Test
    fun `behandler søknad for person i DP-regime som er arbeidssøker - endrer status til DAGPENGERBRUKER og sender overtakelsesmelding`() {
        val søknadHendelse = lagSøknadHendelse()
        val person = lagPersonMedAktivArbeidssøkerperiode()
        person.addObserver(personObserver)
        every { personService.hentEllerOpprettPerson(ident) } returns person

        søknadService.behandle(søknadHendelse)

        person.status shouldBe Status.DAGPENGERBRUKER
        verify(exactly = 1) { personObserver.sendOvertakelsesmelding(person) }
    }

    @Test
    fun `hopper over søknad som allerede er behandlet (duplikat referanseId)`() {
        val søknadHendelse = lagSøknadHendelse()
        val person = lagPerson(ARENA)
        person.hendelser.add(søknadHendelse)
        every { personService.hentEllerOpprettPerson(ident) } returns person

        søknadService.behandle(søknadHendelse)

        person.hendelser.size shouldBe 1
        verify(exactly = 0) { personRepository.oppdaterPerson(any()) }
        verify(exactly = 0) { arbeidssøkerMediator.behandle(person.ident) }
    }

    @Test
    fun `hentSøknader returnerer en liste med 3 elementer hvis personen eksisterer og har 3 søknader`() {
        val person = lagPerson(DP)
        person.hendelser.addAll(
            listOf(
                lagSøknadHendelse(),
                lagSøknadHendelse(),
                lagVedtakHendelse(),
                lagSøknadHendelse(),
            ),
        )
        every { personService.hentPerson(any<String>()) } returns person

        søknadService.hentSøknader(ident).size shouldBe 3
    }

    @Test
    fun `hentSøknader returnerer en tom liste hvis personen eksisterer men ikke har søknader`() {
        every { personService.hentPerson(any<String>()) } returns lagPerson(DP)

        søknadService.hentSøknader(ident).isEmpty() shouldBe true
    }

    @Test
    fun `hentSøknader kaster forventet exception hvis personen ikke eksisterer`() {
        every { personService.hentPerson(any<String>()) } returns null

        shouldThrow<PersonNotFoundException> { søknadService.hentSøknader(ident) }
    }

    private fun lagSøknadHendelse() =
        SøknadHendelse(
            korrelasjonsId = null,
            ident = ident,
            dato = dato,
            startDato = dato,
            referanseId = newUuid().toString(),
        )

    private fun lagVedtakHendelse() =
        VedtakHendelse(
            korrelasjonsId = null,
            ident = ident,
            startDato = dato,
            referanseId = newUuid().toString(),
            utfall = true,
        )

    private fun lagPerson(ansvarligSystem: AnsvarligSystem): Person {
        val person = Person(ident)
        person.setAnsvarligSystem(ansvarligSystem)
        return person
    }

    private fun lagPersonMedAktivArbeidssøkerperiode(): Person {
        val person = lagPerson(DP)
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
