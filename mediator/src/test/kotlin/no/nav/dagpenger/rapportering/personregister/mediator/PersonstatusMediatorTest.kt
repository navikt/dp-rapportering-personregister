package no.nav.dagpenger.rapportering.personregister.mediator

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import no.nav.dagpenger.rapportering.personregister.mediator.connector.ArbeidssøkerConnector
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.hendelser.SøknadHendelse
import no.nav.dagpenger.rapportering.personregister.mediator.hendelser.VedtakHendelse
import no.nav.dagpenger.rapportering.personregister.mediator.service.ArbeidssøkerService
import no.nav.dagpenger.rapportering.personregister.modell.Hendelse
import no.nav.dagpenger.rapportering.personregister.modell.Kildesystem
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.Status
import no.nav.dagpenger.rapportering.personregister.modell.arbeidssøker.ArbeidssøkerperiodeResponse
import no.nav.dagpenger.rapportering.personregister.modell.arbeidssøker.BrukerResponse
import no.nav.dagpenger.rapportering.personregister.modell.arbeidssøker.MetadataResponse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class PersonstatusMediatorTest {
    private lateinit var rapidsConnection: TestRapid
    private lateinit var personRepository: PersonRepository
    private lateinit var personstatusMediator: PersonstatusMediator
    private lateinit var arbeidssøkerConnector: ArbeidssøkerConnector
    private lateinit var arbeidssøkerService: ArbeidssøkerService

    @BeforeEach
    fun setup() {
        rapidsConnection = TestRapid()
        personRepository = PersonRepositoryFaker()
        arbeidssøkerConnector = mockk<ArbeidssøkerConnector>()
        arbeidssøkerService = ArbeidssøkerService(arbeidssøkerConnector)
        personstatusMediator = PersonstatusMediator(personRepository, arbeidssøkerService)
    }

    @Test
    fun `kan behandle en ny hendelse med ny person`() {
        coEvery { arbeidssøkerConnector.hentSisteArbeidssøkerperiode(any()) } returns arbeidssøkerperiodeResponse(true)
        val ident = "12345678910"
        val søknadId = "123"

        val søknadHendelse =
            SøknadHendelse(
                ident = ident,
                referanseId = søknadId,
                dato = LocalDateTime.now(),
            )

        personRepository.hentPerson(ident) shouldBe null

        personstatusMediator
            .behandle(søknadHendelse)

        personRepository.hentPerson(ident)?.apply {
            ident shouldBe ident
            status shouldBe Status.SØKT
        }
    }

    @Test
    fun `kan behandle eksisterende person`() {
        coEvery { arbeidssøkerConnector.hentSisteArbeidssøkerperiode(any()) } returns arbeidssøkerperiodeResponse(true)
        val ident = "12345678910"
        val søknadId = "123"
        val dato = LocalDateTime.now()

        val søknadHendelse =
            SøknadHendelse(
                ident = ident,
                referanseId = søknadId,
                dato = dato,
            )

        val hendelse =
            Hendelse(
                ident = ident,
                referanseId = søknadId,
                dato = dato,
                status = Status.SØKT,
                kilde = Kildesystem.Søknad,
            )

        val person = Person(ident).apply { behandle(hendelse) }
        personRepository.lagrePerson(person)

        personstatusMediator.behandle(søknadHendelse)

        personRepository.hentPerson(ident)?.apply {
            ident shouldBe ident
            status shouldBe Status.SØKT
        }
    }

    @Test
    fun `kan behandle vedtak hendelse`() {
        val ident = "12345678910"
        val søknadId = "123"
        val dato = LocalDateTime.now()

        personstatusMediator.behandle(
            VedtakHendelse(
                ident = ident,
                referanseId = søknadId,
                dato = dato,
                status = Status.INNVILGET,
            ),
        )

        personRepository.hentPerson(ident)?.apply {
            ident shouldBe ident
            status shouldBe Status.INNVILGET
        }
    }

    @Test
    fun `kan behandle vedtak hendelse for eksisterende person`() {
        coEvery { arbeidssøkerConnector.hentSisteArbeidssøkerperiode(any()) } returns arbeidssøkerperiodeResponse()
        val ident = "12345678910"
        val søknadId = "123"
        val dato = LocalDateTime.now().minusDays(1)

        personstatusMediator.behandle(
            SøknadHendelse(
                ident = ident,
                referanseId = søknadId,
                dato = dato,
            ),
        )

        personstatusMediator.behandle(
            VedtakHendelse(
                ident = ident,
                referanseId = søknadId,
                dato = dato.plusDays(1),
                status = Status.INNVILGET,
            ),
        )

        personRepository.hentPerson(ident)?.apply {
            ident shouldBe ident
            status shouldBe Status.INNVILGET
            status(dato) shouldBe Status.SØKT
            status(dato.minusDays(1)) shouldBe Status.ARBS
        }
    }

    @Test
    fun `kan behandle søknad hendelse for person som ikke eksisterer i databasen og ikke er registrert som arbeidssøker`() {
        coEvery { arbeidssøkerConnector.hentSisteArbeidssøkerperiode(any()) } returns emptyList()
        val ident = "12345678910"
        val søknadId = "123"
        val dato = LocalDateTime.now()

        personstatusMediator.behandle(
            SøknadHendelse(
                ident = ident,
                referanseId = søknadId,
                dato = dato,
            ),
        )

        personRepository.hentPerson(ident)?.apply {
            ident shouldBe ident
            status shouldBe Status.SØKT
            statusHistorikk.allItems().size shouldBe 1
        }
    }
}

class PersonRepositoryFaker : PersonRepository {
    private val personliste = mutableMapOf<String, Person>()

    override fun hentPerson(ident: String): Person? = personliste[ident]

    override fun finnesPerson(ident: String): Boolean = personliste.contains(ident)

    override fun lagrePerson(person: Person) {
        personliste[person.ident] = person
    }

    override fun oppdaterPerson(person: Person) {
        personliste.replace(person.ident, person)
    }

    override fun hentAnallPersoner(): Int = personliste.size

    override fun hentAntallHendelser(): Int = personliste.values.sumOf { it.hendelser.size }
}

fun arbeidssøkerperiodeResponse(
    avsluttet: Boolean = false,
    tidspunktForRegistrering: LocalDateTime = LocalDateTime.now().minusWeeks(3),
) = listOf(
    ArbeidssøkerperiodeResponse(
        periodeId = UUID.randomUUID(),
        startet =
            MetadataResponse(
                tidspunkt = tidspunktForRegistrering,
                utfoertAv =
                    BrukerResponse(
                        type = "SLUTTBRUKER",
                        id = "12345678910",
                    ),
                kilde = "kilde",
                aarsak = "årsak",
                tidspunktFraKilde = null,
            ),
        avsluttet =
            if (avsluttet) {
                MetadataResponse(
                    tidspunkt = LocalDateTime.now(),
                    utfoertAv =
                        BrukerResponse(
                            type = "SYSTEM",
                            id = "systemid",
                        ),
                    kilde = "kilde",
                    aarsak = "årsak",
                    tidspunktFraKilde = null,
                )
            } else {
                null
            },
    ),
)
