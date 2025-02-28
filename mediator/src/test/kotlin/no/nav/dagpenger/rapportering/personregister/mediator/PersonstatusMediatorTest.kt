package no.nav.dagpenger.rapportering.personregister.mediator

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import no.nav.dagpenger.rapportering.personregister.mediator.connector.ArbeidssøkerConnector
import no.nav.dagpenger.rapportering.personregister.mediator.db.InMemoryPersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.service.ArbeidssøkerService
import no.nav.dagpenger.rapportering.personregister.mediator.utils.arbeidssøkerResponse
import no.nav.dagpenger.rapportering.personregister.mediator.utils.kafka.MockKafkaProducer
import no.nav.dagpenger.rapportering.personregister.modell.AnnenMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.Arbeidssøkerperiode
import no.nav.dagpenger.rapportering.personregister.modell.DagpengerMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.Dagpengerbruker
import no.nav.dagpenger.rapportering.personregister.modell.IkkeDagpengerbruker
import no.nav.dagpenger.rapportering.personregister.modell.MeldepliktHendelse
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.PersonObserver
import no.nav.dagpenger.rapportering.personregister.modell.SøknadHendelse
import no.nav.dagpenger.rapportering.personregister.modell.gjeldende
import no.nav.paw.bekreftelse.paavegneav.v1.PaaVegneAv
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class PersonstatusMediatorTest {
    private lateinit var rapidsConnection: TestRapid
    private lateinit var personRepository: PersonRepository
    private lateinit var arbeidssøkerConnector: ArbeidssøkerConnector
    private lateinit var overtaBekreftelseKafkaProdusent: MockKafkaProducer<PaaVegneAv>
    private lateinit var personstatusMediator: PersonstatusMediator
    private lateinit var arbeidssøkerService: ArbeidssøkerService
    private lateinit var arbeidssøkerMediator: ArbeidssøkerMediator
    private val overtaBekreftelseTopic = "paa_vegne_av"

    private val personObserver = mockk<PersonObserver>(relaxed = true)

    @BeforeEach
    fun setup() {
        rapidsConnection = TestRapid()
        personRepository = InMemoryPersonRepository()
        arbeidssøkerConnector = mockk<ArbeidssøkerConnector>(relaxed = true)
        overtaBekreftelseKafkaProdusent = MockKafkaProducer()
        arbeidssøkerService = ArbeidssøkerService(arbeidssøkerConnector)
        arbeidssøkerMediator = ArbeidssøkerMediator(arbeidssøkerService, personRepository)
        personstatusMediator = PersonstatusMediator(personRepository, arbeidssøkerMediator, listOf(personObserver))
    }

    private val ident = "12345678910"
    private val nå = LocalDateTime.now()
    private val tidligere = nå.minusDays(1)
    private val periodeId = UUID.randomUUID()

    @Nested
    inner class SøknadHendelser {
        @Test
        fun `søknad for ny person som ikke er arbeidssøkerregistrert`() {
            testPerson {
                coEvery { arbeidssøkerConnector.hentSisteArbeidssøkerperiode(any()) } returns emptyList()

                personstatusMediator.behandle(søknadHendelse())

                status shouldBe IkkeDagpengerbruker
                arbeidssøkerperioder shouldHaveSize 0
            }
        }

        @Test
        fun `søknad for ny person som er arbeidssøkerregistrert men ikke oppfyller kravet`() {
            testPerson {
                coEvery { arbeidssøkerConnector.hentSisteArbeidssøkerperiode(any()) } returns listOf(arbeidssøkerResponse(periodeId))

                personstatusMediator.behandle(søknadHendelse())

                status shouldBe IkkeDagpengerbruker
                arbeidssøkerperioder.gjeldende?.periodeId shouldBe periodeId
                arbeidssøkerperioder.gjeldende?.overtattBekreftelse shouldBe false
            }
        }

        @Test
        fun `søknad for ny person som er arbeidssøkerregistrert som oppfyller kravet`() {
            testPerson {
                meldeplikt = true
                meldegruppe = "DAGP"
                coEvery { arbeidssøkerConnector.hentSisteArbeidssøkerperiode(any()) } returns listOf(arbeidssøkerResponse(periodeId))

                personstatusMediator.behandle(søknadHendelse())

                status shouldBe Dagpengerbruker
                arbeidssøkerperioder.gjeldende?.periodeId shouldBe periodeId
                arbeidssøkerperioder.gjeldende?.overtattBekreftelse shouldBe true
            }
        }

        @Test
        fun `søknad for eksisterende person som ikke er arbeidssøkerregistrert`() {
            testPerson {
                coEvery { arbeidssøkerConnector.hentSisteArbeidssøkerperiode(any()) } returns emptyList()

                personstatusMediator.behandle(søknadHendelse())

                status shouldBe IkkeDagpengerbruker
                arbeidssøkerperioder shouldHaveSize 0
            }
        }

        @Test
        fun `søknad for eksisterende person som er arbeidssøkerregistrert`() {
            testPerson {
                coEvery { arbeidssøkerConnector.hentSisteArbeidssøkerperiode(any()) } returns
                    listOf(
                        arbeidssøkerResponse(periodeId),
                    )

                personstatusMediator.behandle(søknadHendelse())

                status shouldBe IkkeDagpengerbruker
                arbeidssøkerperioder shouldHaveSize 1
                arbeidssøkerperioder.gjeldende?.overtattBekreftelse shouldBe false
            }
        }

        @Test
        fun `overtar arbeidssøkerbekreftelse for søker som er oppfyller kravet`() {
            testPerson {
                meldeplikt = true
                meldegruppe = "DAGP"

                coEvery { arbeidssøkerConnector.hentSisteArbeidssøkerperiode(any()) } returns
                    listOf(arbeidssøkerResponse(periodeId))

                personstatusMediator.behandle(søknadHendelse())

                status shouldBe Dagpengerbruker
                arbeidssøkerperioder shouldHaveSize 1
                arbeidssøkerperioder.gjeldende?.overtattBekreftelse shouldBe true
                personObserver skalHaSendtOvertakelseFor this
            }
        }
    }

    @Nested
    inner class Meldegruppeendring {
        @Test
        fun `dagpengerhendelse for ny person som ikke oppfyller kravet`() {
            arbeidssøker {
                personstatusMediator.behandle(dagpengerMeldegruppeHendelse())

                status shouldBe IkkeDagpengerbruker
                personObserver skalIkkeHaSendtOvertakelseFor this
            }
        }

        @Test
        fun `dagpengerhendelse for ny person som oppfyller kravet`() {
            arbeidssøker {
                meldeplikt = true

                personstatusMediator.behandle(dagpengerMeldegruppeHendelse())

                status shouldBe Dagpengerbruker
                personObserver skalHaSendtOvertakelseFor this
            }
        }

        @Test
        fun `dagpengerhendelse for eksisterende person som ikke er dagpengerbruker`() {
            arbeidssøker {
                meldeplikt = true

                statusHistorikk.put(nå.minusDays(1), IkkeDagpengerbruker)
                personRepository.oppdaterPerson(this)

                status shouldBe IkkeDagpengerbruker

                personstatusMediator.behandle(dagpengerMeldegruppeHendelse())

                status shouldBe Dagpengerbruker
                personObserver skalHaSendtOvertakelseFor this
            }
        }

        @Test
        fun `dagpengerhendelse for eksisterende person som er dagpengerbruker`() {
            arbeidssøker {
                meldeplikt = true
                meldegruppe = "DAGP"

                statusHistorikk.put(tidligere, Dagpengerbruker)
                personRepository.oppdaterPerson(this)

                status shouldBe Dagpengerbruker

                personstatusMediator.behandle(dagpengerMeldegruppeHendelse())

                status shouldBe Dagpengerbruker
            }
        }

        @Test
        fun `annenMeldegruppeHendelse for ny person`() {
            testPerson {
                personstatusMediator.behandle(annenMeldegruppeHendelse())

                status shouldBe IkkeDagpengerbruker
            }
        }

        @Test
        fun `annenMeldegruppeHendelse for eksisterende person som er dagpengerbruker`() {
            testPerson {
                statusHistorikk.put(tidligere, Dagpengerbruker)
                personRepository.oppdaterPerson(this)

                status shouldBe Dagpengerbruker

                personstatusMediator.behandle(annenMeldegruppeHendelse())

                status shouldBe IkkeDagpengerbruker
            }
        }

        @Test
        fun `annenMeldegruppeHendelse for eksisterende person som ikke er dagpengerbruker`() {
            testPerson {
                statusHistorikk.put(tidligere, IkkeDagpengerbruker)
                personRepository.oppdaterPerson(this)

                status shouldBe IkkeDagpengerbruker

                personstatusMediator.behandle(annenMeldegruppeHendelse())

                status shouldBe IkkeDagpengerbruker
            }
        }

        @Test
        fun `overtar arbeidssøker bekreftelse når man blir dagpengerbruker`() {
            arbeidssøker {
                meldeplikt = true

                statusHistorikk.put(tidligere, IkkeDagpengerbruker)
                personRepository.oppdaterPerson(this)

                status shouldBe IkkeDagpengerbruker

                personstatusMediator.behandle(dagpengerMeldegruppeHendelse())

                status shouldBe Dagpengerbruker
                personObserver skalHaSendtOvertakelseFor this
            }
        }

        @Test
        fun `sender ikke overtakelsesmelding dersom vi allerede har overtatt arbeidssøker bekreftelse`() {
            arbeidssøker(overtattBekreftelse = true) {
                meldeplikt = true
                statusHistorikk.put(tidligere, IkkeDagpengerbruker)
                personRepository.oppdaterPerson(this)

                status shouldBe IkkeDagpengerbruker

                personstatusMediator.behandle(dagpengerMeldegruppeHendelse())

                status shouldBe Dagpengerbruker
                personObserver skalIkkeHaSendtOvertakelseFor this
            }
        }

        @Test
        fun `frasier arbeidssøker bekreftelse`() {
            arbeidssøker(overtattBekreftelse = true) {
                statusHistorikk.put(tidligere, Dagpengerbruker)
                personRepository.oppdaterPerson(this)

                status shouldBe Dagpengerbruker

                personstatusMediator.behandle(annenMeldegruppeHendelse())

                status shouldBe IkkeDagpengerbruker
                personObserver skalHaFrasagtAnsvaretFor this
            }
        }
    }

    private fun testPerson(block: Person.() -> Unit) {
        val person = Person(ident = ident)
        personRepository.lagrePerson(person)
        person.apply(block)
    }

    private fun arbeidssøker(
        overtattBekreftelse: Boolean = false,
        block: Person.() -> Unit,
    ) {
        val person =
            Person(
                ident = ident,
                arbeidssøkerperioder = mutableListOf(Arbeidssøkerperiode(periodeId, ident, tidligere, null, overtattBekreftelse)),
            )
        personRepository.lagrePerson(person)
        person.apply(block)
    }

    private fun søknadHendelse(
        dato: LocalDateTime = nå,
        referanseId: String = "123",
    ) = SøknadHendelse(ident, dato, referanseId)

    private fun dagpengerMeldegruppeHendelse(
        dato: LocalDateTime = nå,
        referanseId: String = "123",
    ) = DagpengerMeldegruppeHendelse(ident, dato, startDato = dato, sluttDato = null, "DAGP", referanseId)

    private fun annenMeldegruppeHendelse(
        dato: LocalDateTime = nå,
        referanseId: String = "123",
    ) = AnnenMeldegruppeHendelse(ident, dato, startDato = dato, sluttDato = null, "ARBS", referanseId)

    private fun meldepliktHendelse(
        dato: LocalDateTime = nå,
        referanseId: String = "123",
    ) = MeldepliktHendelse(
        ident = ident,
        dato = dato,
        startDato = dato,
        sluttDato = null,
        statusMeldeplikt = true,
        referanseId = referanseId,
    )
}

infix fun PersonObserver.skalHaSendtOvertakelseFor(person: Person) {
    verify(exactly = 1) { overtaArbeidssøkerBekreftelse(person) }
}

infix fun PersonObserver.skalIkkeHaSendtOvertakelseFor(person: Person) {
    verify(exactly = 0) { overtaArbeidssøkerBekreftelse(person) }
}

infix fun PersonObserver.skalHaFrasagtAnsvaretFor(person: Person) {
    verify(exactly = 1) { frasiArbeidssøkerBekreftelse(person) }
}
