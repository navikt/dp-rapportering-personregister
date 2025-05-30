package no.nav.dagpenger.rapportering.personregister.mediator

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import no.nav.dagpenger.rapportering.personregister.mediator.db.InMemoryPersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.service.ArbeidssøkerService
import no.nav.dagpenger.rapportering.personregister.mediator.service.PersonService
import no.nav.dagpenger.rapportering.personregister.mediator.utils.MetrikkerTestUtil.actionTimer
import no.nav.dagpenger.rapportering.personregister.modell.Arbeidssøkerperiode
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.PersonObserver
import no.nav.dagpenger.rapportering.personregister.modell.Status.DAGPENGERBRUKER
import no.nav.dagpenger.rapportering.personregister.modell.Status.IKKE_DAGPENGERBRUKER
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class ArbeidssøkerMediatorTest {
    private val arbeidssøkerService = mockk<ArbeidssøkerService>(relaxed = true)
    private lateinit var personRepository: PersonRepository
    private val personObserver = mockk<PersonObserver>(relaxed = true)
    private val personService = mockk<PersonService>()

    private lateinit var arbeidssøkerMediator: ArbeidssøkerMediator
    val ident = "12345678901"

    @BeforeEach
    fun setup() {
        personRepository = InMemoryPersonRepository()
        arbeidssøkerMediator =
            ArbeidssøkerMediator(
                arbeidssøkerService = arbeidssøkerService,
                personRepository = personRepository,
                personObservers = listOf(personObserver),
                actionTimer = actionTimer,
                personService = personService,
            )
    }

    @Test
    fun `skal behandle startet arbeidssøkerperiode for person som oppfyller kravet`() {
        val periodeId = UUID.randomUUID()
        val person = testPerson(ident, "DAGP", meldeplikt = true)

        every { personService.hentPerson(ident) } returns person

        arbeidssøkerMediator.behandle(
            Arbeidssøkerperiode(
                periodeId = periodeId,
                ident = ident,
                startet = LocalDateTime.now(),
                avsluttet = null,
                overtattBekreftelse = null,
            ),
        )

        person.status shouldBe DAGPENGERBRUKER
        personObserver skalHaSendtOvertakelseFor person
    }

    @Test
    fun `skal behandle startet arbeidssøkerperiode for person som ikke oppfyller kravet`() {
        val periodeId = UUID.randomUUID()
        val person = testPerson(ident, "ARBS", meldeplikt = true)

        every { personService.hentPerson(ident) } returns person

        arbeidssøkerMediator.behandle(
            Arbeidssøkerperiode(
                periodeId = periodeId,
                ident = ident,
                startet = LocalDateTime.now().minusDays(1),
                avsluttet = null,
                overtattBekreftelse = null,
            ),
        )

        person.status shouldBe IKKE_DAGPENGERBRUKER
        personObserver skalIkkeHaSendtOvertakelseFor person
    }

    @Test
    fun `skal behandle avsluttet arbeidssøkerperiode for dagpengerbruk`() {
        val periodeId = UUID.randomUUID()
        val arbeidssøkerperiode =
            Arbeidssøkerperiode(
                periodeId = periodeId,
                ident = ident,
                startet = LocalDateTime.now().minusDays(1),
                avsluttet = null,
                overtattBekreftelse = true,
            )

        val person =
            testPerson(ident, "DAGP", meldeplikt = true).apply {
                statusHistorikk.put(LocalDateTime.now(), DAGPENGERBRUKER)
            }

        every { personService.hentPerson(ident) } returns person

        arbeidssøkerMediator.behandle(
            arbeidssøkerperiode.copy(avsluttet = LocalDateTime.now()),
        )

        person.status shouldBe IKKE_DAGPENGERBRUKER
        person.arbeidssøkerperioder
            .find { it.periodeId == periodeId }
            ?.overtattBekreftelse shouldBe false
    }

    @Test
    fun `kan hente aktiv arbeidssøkerstatus og triggerer riktig hendelse for eksisterende person`() {
        val periodeId = UUID.randomUUID()
        val person = testPerson(ident, "DAGP", meldeplikt = true)

        coEvery { arbeidssøkerService.hentSisteArbeidssøkerperiode("12345678901") } returns
            Arbeidssøkerperiode(
                periodeId = periodeId,
                ident = ident,
                startet = LocalDateTime.now().minusDays(1),
                avsluttet = null,
                overtattBekreftelse = null,
            )
        every { personService.hentPerson(ident) } returns person

        arbeidssøkerMediator.behandle(ident)

        person.status shouldBe DAGPENGERBRUKER
        personObserver skalHaSendtOvertakelseFor person
    }

    @Test
    fun `kan hente aktiv arbeidssøkerstatus og triggerer riktig hendelse for ukjent person`() {
        val periodeId = UUID.randomUUID()
        val person = testPerson(ident, "AP", meldeplikt = true)

        coEvery { arbeidssøkerService.hentSisteArbeidssøkerperiode("12345678901") } returns
            Arbeidssøkerperiode(
                periodeId = periodeId,
                ident = ident,
                startet = LocalDateTime.now().minusDays(1),
                avsluttet = null,
                overtattBekreftelse = null,
            )
        every { personService.hentPerson(ident) } returns null

        arbeidssøkerMediator.behandle(ident)

        person.status shouldBe IKKE_DAGPENGERBRUKER
    }

    @Test
    fun `kan hente avsluttet arbeidssøkerstatus og triggerer riktig hendelse for eksisterende person`() {
        val periodeId = UUID.randomUUID()

        val arbeidsøkerperioder =
            Arbeidssøkerperiode(
                periodeId = periodeId,
                ident = ident,
                startet = LocalDateTime.now().minusDays(1),
                avsluttet = null,
                overtattBekreftelse = true,
            )
        val person =
            testPerson(ident, "DAGP", meldeplikt = true, mutableListOf(arbeidsøkerperioder))
                .apply { statusHistorikk.put(LocalDateTime.now(), DAGPENGERBRUKER) }
        coEvery { arbeidssøkerService.hentSisteArbeidssøkerperiode("12345678901") } returns
            arbeidsøkerperioder.copy(avsluttet = LocalDateTime.now())
        every { personService.hentPerson(ident) } returns person

        arbeidssøkerMediator.behandle(ident)

        person.status shouldBe IKKE_DAGPENGERBRUKER
        person.arbeidssøkerperioder
            .find { it.periodeId == periodeId }
            ?.overtattBekreftelse shouldBe false
    }

    @Test
    fun `kan håndtere feil ved henting av arbeidssøkerperiode`() {
        personRepository.lagrePerson(testPerson(ident, "DAGP", meldeplikt = true))
        coEvery { arbeidssøkerService.hentSisteArbeidssøkerperiode(ident) } throws
            RuntimeException("Feil ved henting av arbeidssøkerperiode")

        arbeidssøkerMediator.behandle(ident)

        personRepository.hentPerson(ident)?.status shouldBe IKKE_DAGPENGERBRUKER
    }

    @Test
    fun `forkaster endringer og prøver på nytt hvis personens versjon ikke stemmer`() {
        val arbeidsøkerperioder =
            Arbeidssøkerperiode(
                periodeId = UUID.randomUUID(),
                ident = ident,
                startet = LocalDateTime.now().minusDays(1),
                avsluttet = null,
                overtattBekreftelse = true,
            )
        coEvery { arbeidssøkerService.hentSisteArbeidssøkerperiode("12345678901") } returns
            arbeidsøkerperioder.copy(avsluttet = LocalDateTime.now())
        val person = testPerson(ident = ident)
        every { personService.hentPerson(ident) } returns person

        personRepository.lagrePerson(person)

        arbeidssøkerMediator.behandle(ident)

        personRepository.hentPerson(ident)?.versjon shouldBe 2
    }
}

fun testPerson(
    ident: String,
    meldegruppe: String = "DAGP",
    meldeplikt: Boolean = true,
    arbeidsøkerperioder: MutableList<Arbeidssøkerperiode> = mutableListOf(),
) = Person(
    ident = ident,
    arbeidssøkerperioder = arbeidsøkerperioder,
).apply {
    this.setMeldegruppe(meldegruppe)
    setMeldeplikt(meldeplikt)
}
