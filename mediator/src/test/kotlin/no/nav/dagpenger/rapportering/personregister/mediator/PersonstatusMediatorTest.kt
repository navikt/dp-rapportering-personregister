package no.nav.dagpenger.rapportering.personregister.mediator

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import no.nav.dagpenger.rapportering.personregister.mediator.connector.ArbeidssøkerConnector
import no.nav.dagpenger.rapportering.personregister.mediator.connector.RecordKeyResponse
import no.nav.dagpenger.rapportering.personregister.mediator.db.ArbeidssøkerRepository
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.service.ArbeidssøkerService
import no.nav.dagpenger.rapportering.personregister.mediator.utils.MockKafkaProducer
import no.nav.dagpenger.rapportering.personregister.mediator.utils.arbeidssøkerResponse
import no.nav.dagpenger.rapportering.personregister.modell.AnnenMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.Arbeidssøkerperiode
import no.nav.dagpenger.rapportering.personregister.modell.Dagpengerbruker
import no.nav.dagpenger.rapportering.personregister.modell.IkkeDagpengerbruker
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.SøknadHendelse
import no.nav.paw.bekreftelse.paavegneav.v1.PaaVegneAv
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class PersonstatusMediatorTest {
    private lateinit var rapidsConnection: TestRapid
    private lateinit var personRepository: PersonRepository
    private lateinit var arbeidssøkerRepository: ArbeidssøkerRepository
    private lateinit var arbeidssøkerConnector: ArbeidssøkerConnector
    private lateinit var overtaBekreftelseKafkaProdusent: MockKafkaProducer<PaaVegneAv>
    private lateinit var personstatusMediator: PersonstatusMediator
    private lateinit var arbeidssøkerService: ArbeidssøkerService
    private lateinit var arbeidssøkerMediator: ArbeidssøkerMediator
    private val overtaBekreftelseTopic = "paa_vegne_av"

    @BeforeEach
    fun setup() {
        rapidsConnection = TestRapid()
        personRepository = PersonRepositoryFaker()
        arbeidssøkerRepository = ArbeidssøkerRepositoryFaker()
        arbeidssøkerConnector = mockk<ArbeidssøkerConnector>(relaxed = true)
        overtaBekreftelseKafkaProdusent = MockKafkaProducer()
        arbeidssøkerService =
            ArbeidssøkerService(
                personRepository,
                arbeidssøkerRepository,
                arbeidssøkerConnector,
                overtaBekreftelseKafkaProdusent,
                overtaBekreftelseTopic,
            )
        arbeidssøkerMediator = ArbeidssøkerMediator(arbeidssøkerService)
        personstatusMediator = PersonstatusMediator(personRepository, arbeidssøkerMediator)
    }

    @Test
    fun `kan behandle en ny hendelse med ny person`() {
        val periodeId = UUID.randomUUID()
        val recordKey = 1234L
        coEvery { arbeidssøkerConnector.hentSisteArbeidssøkerperiode(any()) } returns listOf(arbeidssøkerResponse(periodeId))
        coEvery { arbeidssøkerConnector.hentRecordKey(any()) } returns RecordKeyResponse(recordKey)
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

        with(overtaBekreftelseKafkaProdusent.meldinger) {
            size shouldBe 1
            with(first()) {
                topic() shouldBe overtaBekreftelseTopic
                key() shouldBe recordKey
                value().periodeId shouldBe periodeId
                value().bekreftelsesloesning shouldBe no.nav.paw.bekreftelse.paavegneav.v1.vo.Bekreftelsesloesning.DAGPENGER
            }
        }

        personRepository.hentPerson(ident)?.apply {
            ident shouldBe ident
            status shouldBe Dagpengerbruker
        }
    }

    @Test
    fun `kan behandle eksisterende person`() {
        val periodeId = UUID.randomUUID()
        coEvery { arbeidssøkerConnector.hentSisteArbeidssøkerperiode(any()) } returns listOf(arbeidssøkerResponse(periodeId))
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
            SøknadHendelse(
                ident = ident,
                referanseId = søknadId,
                dato = dato,
            )

        val person = Person(ident).apply { behandle(hendelse) }
        personRepository.lagrePerson(person)

        personstatusMediator.behandle(søknadHendelse)

        personRepository.hentPerson(ident)?.apply {
            ident shouldBe ident
            status shouldBe Dagpengerbruker
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
            status shouldBe Dagpengerbruker
        }
    }

    @Test
    fun `kan behandle stans hendelse for eksisterende person`() {
        val ident = "12345678910"
        val søknadId = "123"
        val dato = LocalDateTime.now().minusDays(1)

        personstatusMediator.behandle(
            AnnenMeldegruppeHendelse(
                ident = ident,
                meldegruppeKode = "ARBS",
                dato = dato.plusDays(1),
                referanseId = UUID.randomUUID().toString(),
            ),
        )

        personRepository.hentPerson(ident)?.apply {
            ident shouldBe ident
            status shouldBe IkkeDagpengerbruker
        }
    }

    @Test
    fun `kan behandle stans hendelse for person som ikke eksisterer i databasen`() {
        val ident = "12345678910"
        val dato = LocalDateTime.now().minusDays(1)

        personstatusMediator.behandle(
            AnnenMeldegruppeHendelse(
                ident = ident,
                meldegruppeKode = "ARBS",
                dato = dato.plusDays(1),
                referanseId = UUID.randomUUID().toString(),
            ),
        )

        personRepository
            .hentPerson(ident)
            ?.apply {
                ident shouldBe ident
                status shouldBe IkkeDagpengerbruker
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

class ArbeidssøkerRepositoryFaker : ArbeidssøkerRepository {
    private val arbeidssøkerperioder = mutableListOf<Arbeidssøkerperiode>()

    override fun hentArbeidssøkerperioder(ident: String): List<Arbeidssøkerperiode> = arbeidssøkerperioder.filter { it.ident == ident }

    override fun lagreArbeidssøkerperiode(arbeidssøkerperiode: Arbeidssøkerperiode) {
        arbeidssøkerperioder.add(arbeidssøkerperiode)
    }

    override fun oppdaterOvertagelse(
        periodeId: UUID,
        overtattBekreftelse: Boolean,
    ) {
        hentPeriode(periodeId)
            .takeIf { it != null }
            ?.let { periode ->
                arbeidssøkerperioder.remove(periode)
                periode
                    .copy(overtattBekreftelse = overtattBekreftelse)
                    .let { arbeidssøkerperioder.add(it) }
            } ?: throw RuntimeException("Fant ikke periode med id $periodeId")
    }

    override fun avsluttArbeidssøkerperiode(
        periodeId: UUID,
        avsluttetDato: LocalDateTime,
    ) {
        hentPeriode(periodeId)
            .takeIf { it != null }
            ?.let { periode ->
                arbeidssøkerperioder.remove(periode)
                periode
                    .copy(avsluttet = avsluttetDato)
                    .let { arbeidssøkerperioder.add(it) }
            } ?: throw RuntimeException("Fant ikke periode med id $periodeId")
    }

    override fun oppdaterPeriodeId(
        ident: String,
        gammelPeriodeId: UUID,
        nyPeriodeId: UUID,
    ) {
        hentPeriode(gammelPeriodeId)
            .takeIf { it != null }
            ?.let { periode ->
                arbeidssøkerperioder.remove(periode)
                periode
                    .copy(periodeId = nyPeriodeId)
                    .let { arbeidssøkerperioder.add(it) }
            } ?: throw RuntimeException("Fant ikke periode med id $gammelPeriodeId")
    }

    private fun hentPeriode(periodeId: UUID) = arbeidssøkerperioder.find { it.periodeId == periodeId }
}
