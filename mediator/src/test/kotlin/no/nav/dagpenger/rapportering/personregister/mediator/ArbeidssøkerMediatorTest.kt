package no.nav.dagpenger.rapportering.personregister.mediator

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import no.nav.dagpenger.rapportering.personregister.mediator.db.ArbeidssøkerRepository
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.service.ArbeidssøkerService
import no.nav.dagpenger.rapportering.personregister.modell.Arbeidssøkerperiode
import no.nav.dagpenger.rapportering.personregister.modell.OvertaBekreftelseLøsning
import no.nav.dagpenger.rapportering.personregister.modell.Person
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class ArbeidssøkerMediatorTest {
    private lateinit var rapidsConnection: TestRapid
    private lateinit var personRepository: PersonRepository
    private lateinit var arbeidssøkerRepository: ArbeidssøkerRepository
    private lateinit var arbeidssøkerService: ArbeidssøkerService
    private lateinit var arbeidssøkerMediator: ArbeidssøkerMediator

    @BeforeEach
    fun setup() {
        rapidsConnection = TestRapid()
        personRepository = PersonRepositoryFaker()
        arbeidssøkerRepository = ArbeidssøkerRepositoryFaker()
        arbeidssøkerService = ArbeidssøkerService(rapidsConnection, personRepository, arbeidssøkerRepository)
        arbeidssøkerMediator = ArbeidssøkerMediator(arbeidssøkerService)
    }

    val person = Person("12345678910")

    private fun arbeidssøkerperiode(
        periodeId: UUID = UUID.randomUUID(),
        ident: String = person.ident,
        startet: LocalDateTime = LocalDateTime.now(),
        avsluttet: LocalDateTime? = null,
        overtattBekreftelse: Boolean? = null,
    ) = Arbeidssøkerperiode(
        periodeId = periodeId,
        ident = ident,
        startet = startet,
        avsluttet = avsluttet,
        overtattBekreftelse = overtattBekreftelse,
    )

    @Test
    fun `kan behandle ny arbeidssøkerperiode`() {
        personRepository.hentPerson(person.ident) shouldBe null
        personRepository.lagrePerson(person)

        val periode = arbeidssøkerperiode()
        arbeidssøkerMediator.behandle(periode)

        with(arbeidssøkerRepository.hentArbeidssøkerperioder(person.ident)) {
            size shouldBe 1
            with(first()) {
                ident shouldBe person.ident
                startet shouldBe periode.startet
                avsluttet shouldBe periode.avsluttet
                overtattBekreftelse shouldBe periode.overtattBekreftelse
            }
        }

        with(rapidsConnection.inspektør) {
            size shouldBe 1
            message(0)["@event_name"].asText() shouldBe "behov_arbeissokerstatus"
            message(0)["@behov"][0].asText() shouldBe "OvertaBekreftelse"
            message(0)["periodeId"].asText() shouldBe periode.periodeId.toString()
            message(0)["ident"].asText() shouldBe person.ident
        }
    }

    @Test
    fun `kan behandle eksisterende arbeidssøkerperiode`() {
        personRepository.lagrePerson(person)

        val periode = arbeidssøkerperiode()
        arbeidssøkerRepository.lagreArbeidssøkerperiode(periode)

        arbeidssøkerRepository.hentArbeidssøkerperioder(person.ident).size shouldBe 1

        val avsluttet = LocalDateTime.now()
        arbeidssøkerMediator.behandle(periode.copy(avsluttet = LocalDateTime.now()))

        with(arbeidssøkerRepository.hentArbeidssøkerperioder(person.ident)) {
            size shouldBe 1
            with(first()) {
                ident shouldBe person.ident
                startet shouldBe periode.startet
                avsluttet shouldBe avsluttet
                overtattBekreftelse shouldBe false
            }
        }

        rapidsConnection.inspektør.size shouldBe 0
    }

    @Test
    fun `utfører ingen operasjoner hvis personen perioden omhandler ikke finnes`() {
        val periode = arbeidssøkerperiode()
        arbeidssøkerMediator.behandle(periode)

        with(arbeidssøkerRepository.hentArbeidssøkerperioder(person.ident)) {
            size shouldBe 0
        }

        rapidsConnection.inspektør.size shouldBe 0
    }

    @Test
    fun `kan behandle overtaBekreftelseLøsning som ikke inneholder feil`() {
        personRepository.lagrePerson(person)

        val periode = arbeidssøkerperiode()
        arbeidssøkerRepository.lagreArbeidssøkerperiode(periode)

        val overtaBekreftelseLøsning =
            OvertaBekreftelseLøsning(
                ident = person.ident,
                periodeId = periode.periodeId,
                løsning = "OK",
                feil = null,
            )

        arbeidssøkerMediator.behandle(overtaBekreftelseLøsning)

        with(arbeidssøkerRepository.hentArbeidssøkerperioder(person.ident)) {
            size shouldBe 1
            with(first()) {
                ident shouldBe person.ident
                startet shouldBe periode.startet
                avsluttet shouldBe periode.avsluttet
                overtattBekreftelse shouldBe true
            }
        }

        rapidsConnection.inspektør.size shouldBe 0
    }

    @Test
    fun `kan behandle overtaBekreftelseLøsning med feil`() {
        personRepository.lagrePerson(person)

        val periode = arbeidssøkerperiode()
        arbeidssøkerRepository.lagreArbeidssøkerperiode(periode)

        val overtaBekreftelseLøsning =
            OvertaBekreftelseLøsning(
                ident = person.ident,
                periodeId = periode.periodeId,
                løsning = null,
                feil = "Feil",
            )

        arbeidssøkerMediator.behandle(overtaBekreftelseLøsning)

        with(arbeidssøkerRepository.hentArbeidssøkerperioder(person.ident)) {
            size shouldBe 1
            with(first()) {
                ident shouldBe person.ident
                startet shouldBe periode.startet
                avsluttet shouldBe periode.avsluttet
                overtattBekreftelse shouldBe null
            }
        }

        with(rapidsConnection.inspektør) {
            size shouldBe 1
            message(0)["@event_name"].asText() shouldBe "behov_arbeissokerstatus"
            message(0)["@behov"][0].asText() shouldBe "OvertaBekreftelse"
            message(0)["periodeId"].asText() shouldBe periode.periodeId.toString()
            message(0)["ident"].asText() shouldBe person.ident
        }
    }

    @Test
    fun `overtaBekreftelseLøsning hvor perioden løsningen omhandler ikke finnes kaster feil`() {
        val overtaBekreftelseLøsning =
            OvertaBekreftelseLøsning(
                ident = person.ident,
                periodeId = UUID.randomUUID(),
                løsning = "OK",
                feil = null,
            )

        shouldThrow<RuntimeException> {
            arbeidssøkerMediator.behandle(overtaBekreftelseLøsning)
        }
    }
}
