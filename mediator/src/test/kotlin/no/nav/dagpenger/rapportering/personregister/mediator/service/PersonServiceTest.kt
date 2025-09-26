package no.nav.dagpenger.rapportering.personregister.mediator.service

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import io.kotest.matchers.shouldBe
import io.mockk.coVerify
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import no.nav.dagpenger.rapportering.personregister.mediator.connector.MeldekortregisterConnector
import no.nav.dagpenger.rapportering.personregister.mediator.connector.PdlConnector
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.modell.Arbeidssøkerperiode
import no.nav.dagpenger.rapportering.personregister.modell.Ident
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.PersonObserver
import no.nav.dagpenger.rapportering.personregister.modell.Status
import no.nav.dagpenger.rapportering.personregister.modell.TemporalCollection
import no.nav.dagpenger.rapportering.personregister.modell.gjeldende
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.AnnenMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.DagpengerMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.MeldepliktHendelse
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.SøknadHendelse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.TimeUnit

class PersonServiceTest {
    val ident = "12345678901"

    private val personRepository = mockk<PersonRepository>()
    private val pdlConnector = mockk<PdlConnector>()
    private val personObserver = mockk<PersonObserver>(relaxed = true)
    private val meldekortregisterConnector = mockk<MeldekortregisterConnector>(relaxed = true)

    private val cache: Cache<String, List<Ident>> =
        Caffeine
            .newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .maximumSize(10)
            .build()

    private val personService =
        PersonService(
            pdlConnector = pdlConnector,
            personRepository = personRepository,
            personObservers = listOf(personObserver),
            cache = cache,
            meldekortregisterConnector = meldekortregisterConnector,
        )

    @BeforeEach
    fun resetCache() {
        cache.invalidateAll()
    }

    @Test
    fun `person finnes ikke returnerer null`() {
        every { personRepository.hentPerson(ident) } returns null
        every { pdlConnector.hentIdenter(any()) } returns
            listOf(Ident(ident = ident, gruppe = Ident.IdentGruppe.FOLKEREGISTERIDENT, historisk = false))

        val person = personService.hentPerson(ident)

        person shouldBe null

        verify(exactly = 1) { personRepository.hentPerson(ident) }
        verify(exactly = 0) { personRepository.oppdaterPerson(any()) }
        verify(exactly = 0) { personRepository.slettPerson(any()) }
        verify(exactly = 0) { personRepository.lagrePerson(any()) }
    }

    @Test
    fun `hentPerson som har oppdatert ident`() {
        every { pdlConnector.hentIdenter(any()) } returns
            listOf(
                Ident(ident = "123456", gruppe = Ident.IdentGruppe.AKTORID, historisk = false),
                Ident(ident = ident, gruppe = Ident.IdentGruppe.FOLKEREGISTERIDENT, historisk = false),
            )
        every { personRepository.hentPerson(any()) } returns Person(ident = ident)

        val person = personService.hentPerson(ident)!!

        person.ident shouldBe ident

        verify(exactly = 1) { personRepository.hentPerson(ident) }
        verify(exactly = 0) { personRepository.oppdaterPerson(any()) }
        verify(exactly = 0) { personRepository.slettPerson(any()) }
        verify(exactly = 0) { personRepository.lagrePerson(any()) }
    }

    @Test
    fun `hentPerson som har historisk ident i DB blir oppdatert med ny ident`() {
        val nyIdent = "10987654321"
        every { pdlConnector.hentIdenter(any()) } returns
            listOf(
                Ident(ident = "123456", gruppe = Ident.IdentGruppe.AKTORID, historisk = false),
                Ident(ident = ident, gruppe = Ident.IdentGruppe.FOLKEREGISTERIDENT, historisk = true),
                Ident(ident = nyIdent, gruppe = Ident.IdentGruppe.FOLKEREGISTERIDENT, historisk = false),
            )
        every { personRepository.hentPerson(ident) } returns Person(ident = ident)
        every { personRepository.hentPerson(nyIdent) } returns null
        justRun { personRepository.oppdaterIdent(any(), nyIdent) }

        val person = personService.hentPerson(ident)!!

        person.ident shouldBe nyIdent

        verify(exactly = 2) { personRepository.hentPerson(any()) }
        verify(exactly = 1) { personRepository.oppdaterIdent(any(), nyIdent) }
        verify(exactly = 0) { personRepository.slettPerson(any()) }
        verify(exactly = 0) { personRepository.lagrePerson(any()) }
        coVerify(exactly = 1) { meldekortregisterConnector.oppdaterIdent(eq(ident), eq(nyIdent)) }
    }

    @Test
    fun `hentPerson der flere personer har blitt merget til én i pdl returnerer merget person`() {
        val nyIdent = "10987654321"
        val aktorId = "123456"
        every { pdlConnector.hentIdenter(any()) } returns
            listOf(
                Ident(ident = aktorId, gruppe = Ident.IdentGruppe.AKTORID, historisk = false),
                Ident(ident = ident, gruppe = Ident.IdentGruppe.FOLKEREGISTERIDENT, historisk = true),
                Ident(ident = nyIdent, gruppe = Ident.IdentGruppe.FOLKEREGISTERIDENT, historisk = false),
            )
        every { personRepository.hentPerson(ident) } returns Person(ident = ident)
        every { personRepository.hentPerson(nyIdent) } returns Person(ident = nyIdent)
        every { personRepository.finnesPerson(nyIdent) } returns true
        justRun { personRepository.slettPerson(ident) }
        justRun { personRepository.slettPerson(aktorId) }
        justRun { personRepository.oppdaterPerson(any()) }

        val person = personService.hentPerson(ident)!!

        person.ident shouldBe nyIdent

        verify(exactly = 2) { personRepository.hentPerson(any()) }
        verify(exactly = 1) { personRepository.slettPerson(any()) }
        verify(exactly = 1) { personRepository.finnesPerson(any()) }
        verify(exactly = 1) { personRepository.oppdaterPerson(any()) }
        verify(exactly = 0) { personRepository.oppdaterIdent(any(), any()) }
        coVerify(exactly = 1) { meldekortregisterConnector.konsoliderIdenter(eq(nyIdent), any()) }
    }

    @Disabled
    @Test
    fun `hentPerson merger person med hendelser og finner riktig arbeidssøkerperiode `() {
        val nyIdent = "98765432101"
        val aktorId = "123456"
        val gammelIdent1 = "12345678901"
        val gammelIdent2 = "12345678902"
        val periodeId1 = UUID.randomUUID()
        val periodeId2 = UUID.randomUUID()
        val person1 =
            person(
                ident = gammelIdent1,
                periodeId = periodeId1,
                startet = LocalDateTime.now().minusYears(1),
                avsluttet = LocalDateTime.now().minusMonths(6),
                meldeplikt = false,
                meldegruppe = "ARBS",
            )
        val person2 =
            person(
                ident = gammelIdent2,
                periodeId = periodeId2,
            )
        every { pdlConnector.hentIdenter(any()) } returns
            listOf(
                Ident(ident = aktorId, gruppe = Ident.IdentGruppe.AKTORID, historisk = false),
                Ident(ident = gammelIdent1, gruppe = Ident.IdentGruppe.FOLKEREGISTERIDENT, historisk = true),
                Ident(ident = gammelIdent2, gruppe = Ident.IdentGruppe.FOLKEREGISTERIDENT, historisk = true),
                Ident(ident = nyIdent, gruppe = Ident.IdentGruppe.FOLKEREGISTERIDENT, historisk = false),
            )
        every { personRepository.hentPerson(gammelIdent1) } returns person1
        every { personRepository.hentPerson(gammelIdent2) } returns person2
        every { personRepository.hentPerson(nyIdent) } returns null
        every { personRepository.finnesPerson(nyIdent) } returns false
        justRun { personRepository.slettPerson(any()) }
        justRun { personRepository.lagrePerson(any()) }

        val person = personService.hentPerson(ident)!!

        with(person) {
            ident shouldBe nyIdent
            arbeidssøkerperioder.size shouldBe 2
            arbeidssøkerperioder.gjeldende!!.periodeId shouldBe person2.arbeidssøkerperioder.first().periodeId
            meldeplikt shouldBe true
            meldegruppe shouldBe "DAGP"
            status shouldBe Status.DAGPENGERBRUKER
        }
        verify(exactly = 2) { personRepository.slettPerson(any()) }
    }

    @Test
    fun `hentPerson returnerer person fra databasen selv om pdl returnerer tom liste`() {
        every { pdlConnector.hentIdenter(any()) } returns emptyList()
        every { personRepository.hentPerson(ident) } returns person(ident, UUID.randomUUID())

        val person = personService.hentPerson(ident)!!
        person.ident shouldBe ident
    }

    @Test
    fun `kan hente personId ved bruk av ident og ident ved bruk av personId`() {
        val personId = 1L
        every { personRepository.hentPersonId(eq(ident)) } returns personId
        every { personRepository.hentIdent(eq(personId)) } returns ident

        personService.hentPersonId(ident) shouldBe personId
        personService.hentIdent(personId) shouldBe ident
    }
}

private fun person(
    ident: String,
    periodeId: UUID,
    startet: LocalDateTime = LocalDateTime.now().minusDays(1),
    avsluttet: LocalDateTime? = null,
    meldeplikt: Boolean = true,
    meldegruppe: String = "DAGP",
) = Person(
    ident = ident,
    arbeidssøkerperioder =
        mutableListOf(
            Arbeidssøkerperiode(
                periodeId,
                ident,
                startet,
                avsluttet,
                meldeplikt && meldegruppe == "DAGP",
            ),
        ),
    statusHistorikk =
        TemporalCollection<Status>()
            .apply {
                put(
                    LocalDateTime.now(),
                    if (avsluttet == null) Status.IKKE_DAGPENGERBRUKER else Status.DAGPENGERBRUKER,
                )
            },
).apply {
    this.setMeldeplikt(meldeplikt)
    this.setMeldegruppe(meldegruppe)
    hendelser.addAll(
        listOf(
            SøknadHendelse(ident, startet.plusHours(1), startet.plusHours(1), UUID.randomUUID().toString()),
            if (meldegruppe ==
                "DAGP"
            ) {
                DagpengerMeldegruppeHendelse(
                    ident,
                    startet.plusHours(1),
                    UUID.randomUUID().toString(),
                    startet.plusHours(1),
                    null,
                    "DAGP",
                    true,
                )
            } else {
                AnnenMeldegruppeHendelse(
                    ident,
                    startet.plusHours(1),
                    UUID.randomUUID().toString(),
                    startet.plusHours(1),
                    null,
                    "ARBS",
                    true,
                )
            },
            MeldepliktHendelse(
                ident,
                startet.plusHours(1),
                UUID.randomUUID().toString(),
                startet.plusHours(1),
                null,
                meldeplikt,
                true,
            ),
        ),
    )
}
