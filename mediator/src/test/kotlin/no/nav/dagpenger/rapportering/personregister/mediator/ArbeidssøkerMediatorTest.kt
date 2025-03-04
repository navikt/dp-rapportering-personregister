package no.nav.dagpenger.rapportering.personregister.mediator

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.service.ArbeidssøkerService
import no.nav.dagpenger.rapportering.personregister.modell.Arbeidssøkerperiode
import no.nav.dagpenger.rapportering.personregister.modell.Dagpengerbruker
import no.nav.dagpenger.rapportering.personregister.modell.IkkeDagpengerbruker
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.PersonObserver
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class ArbeidssøkerMediatorTest {
    private val arbeidssøkerService = mockk<ArbeidssøkerService>(relaxed = true)
    private val personRepository = mockk<PersonRepository>(relaxed = true)
    private val personObserver = mockk<PersonObserver>(relaxed = true)

    private lateinit var arbeidssøkerMediator: ArbeidssøkerMediator

    @BeforeEach
    fun setup() {
        arbeidssøkerMediator =
            ArbeidssøkerMediator(
                arbeidssøkerService = arbeidssøkerService,
                personRepository = personRepository,
                personObservers = listOf(personObserver),
            )
    }

    @Test
    fun `skal behandle startet arbeidssøkerperiode for person som oppfyller kravet`() {
        val ident = "12345678901"
        val periodeId = UUID.randomUUID()
        val person = testPerson(ident, "DAGP", meldeplikt = true)

        every { personRepository.hentPerson(ident) } returns person

        arbeidssøkerMediator.behandle(
            Arbeidssøkerperiode(
                periodeId = periodeId,
                ident = ident,
                startet = LocalDateTime.now(),
                avsluttet = null,
                overtattBekreftelse = null,
            ),
        )

        person.status shouldBe Dagpengerbruker
        personObserver skalHaSendtOvertakelseFor person
    }

    @Test
    fun `skal behandle startet arbeidssøkerperiode for person som ikke oppfyller kravet`() {
        val ident = "12345678901"
        val periodeId = UUID.randomUUID()
        val person = testPerson(ident, "ARBS", meldeplikt = true)

        every { personRepository.hentPerson(ident) } returns person

        arbeidssøkerMediator.behandle(
            Arbeidssøkerperiode(
                periodeId = periodeId,
                ident = ident,
                startet = LocalDateTime.now().minusDays(1),
                avsluttet = null,
                overtattBekreftelse = null,
            ),
        )

        person.status shouldBe IkkeDagpengerbruker
        personObserver skalIkkeHaSendtOvertakelseFor person
    }

    @Test
    fun `skal behandle avsluttet arbeidssøkerperiode for dagpengerbruk`() {
        val ident = "12345678901"
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
                arbeidssøkerperioder.add(arbeidssøkerperiode)
            }

        every { personRepository.hentPerson(ident) } returns person

        arbeidssøkerMediator.behandle(
            arbeidssøkerperiode.copy(avsluttet = LocalDateTime.now()),
        )

        person.status shouldBe IkkeDagpengerbruker
        person.arbeidssøkerperioder
            .find { it.periodeId == periodeId }
            ?.overtattBekreftelse shouldBe false
    }

    @Test
    fun `kan hente aktiv arbeidssøkerstatus og triggerer riktig hendelse for eksisterende person`() {
        val periodeId = UUID.randomUUID()
        val ident = "12345678901"
        val person = testPerson(ident, "DAGP", meldeplikt = true)

        coEvery { arbeidssøkerService.hentSisteArbeidssøkerperiode("12345678901") } returns
            Arbeidssøkerperiode(
                periodeId = periodeId,
                ident = ident,
                startet = LocalDateTime.now().minusDays(1),
                avsluttet = null,
                overtattBekreftelse = null,
            )
        every { personRepository.hentPerson(ident) } returns person

        arbeidssøkerMediator.behandle(ident)

        person.status shouldBe Dagpengerbruker
        personObserver skalHaSendtOvertakelseFor person
    }

    @Test
    fun `kan hente aktiv arbeidssøkerstatus og triggerer riktig hendelse for ukjent person`() {
        val periodeId = UUID.randomUUID()
        val ident = "12345678901"
        val person = testPerson(ident, "AP", meldeplikt = true)

        coEvery { arbeidssøkerService.hentSisteArbeidssøkerperiode("12345678901") } returns
            Arbeidssøkerperiode(
                periodeId = periodeId,
                ident = ident,
                startet = LocalDateTime.now().minusDays(1),
                avsluttet = null,
                overtattBekreftelse = null,
            )
        every { personRepository.hentPerson(ident) } returns null

        arbeidssøkerMediator.behandle(ident)

        person.status shouldBe IkkeDagpengerbruker
    }

    @Test
    fun `kan hente avsluttet arbeidssøkerstatus og triggerer riktig hendelse for eksisterende person`() {
        val periodeId = UUID.randomUUID()
        val ident = "12345678901"

        val arbeidsøkerperioder =
            Arbeidssøkerperiode(
                periodeId = periodeId,
                ident = ident,
                startet = LocalDateTime.now().minusDays(1),
                avsluttet = null,
                overtattBekreftelse = true,
            )
        val person = testPerson(ident, "DAGP", meldeplikt = true, mutableListOf(arbeidsøkerperioder))

        coEvery { arbeidssøkerService.hentSisteArbeidssøkerperiode("12345678901") } returns
            arbeidsøkerperioder.copy(avsluttet = LocalDateTime.now())
        every { personRepository.hentPerson(ident) } returns person

        arbeidssøkerMediator.behandle(ident)

        person.status shouldBe IkkeDagpengerbruker
        person.arbeidssøkerperioder
            .find { it.periodeId == periodeId }
            ?.overtattBekreftelse shouldBe false
    }

    @Test
    fun `kan håndtere feil ved henting av arbeidssøkerperiode`() {
        val ident = "12345678901"
        every { personRepository.hentPerson(ident) } returns testPerson(ident, "DAGP", meldeplikt = true)
        coEvery { arbeidssøkerService.hentSisteArbeidssøkerperiode(ident) } throws
            RuntimeException("Feil ved henting av arbeidssøkerperiode")

        arbeidssøkerMediator.behandle(ident)

        personRepository.hentPerson(ident)?.status shouldBe IkkeDagpengerbruker
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
    this.meldegruppe = meldegruppe
    this.meldeplikt = meldeplikt
}
