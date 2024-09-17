package no.nav.dagpenger.rapportering.personregister.mediator

import io.kotest.matchers.shouldBe
import no.nav.dagpenger.rapportering.personregister.mediator.db.HendelseRepository
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.modell.Hendelse
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.Status
import no.nav.dagpenger.rapportering.personregister.modell.Status.Avslag
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PersonstatusMediatorTest {
    private lateinit var rapidsConnection: TestRapid
    private lateinit var personRepository: PersonRepository
    private lateinit var hendelseRepository: HendelseRepository
    private lateinit var personstatusMediator: PersonstatusMediator

    @BeforeEach
    fun setup() {
        rapidsConnection = TestRapid()
        personRepository = PersonRepositoryFaker()
        hendelseRepository = HendelseRepositoryFaker()
        personstatusMediator = PersonstatusMediator(rapidsConnection, personRepository, hendelseRepository)
    }

    @Test
    fun `kan behandle en ny hendelse med ny person`() {
        val ident = "12345678910"
        val soknadId = "123"

        personRepository.finn(ident) shouldBe null
        hendelseRepository.finnHendelser(ident) shouldBe emptyList()

        personstatusMediator
            .behandle(ident, soknadId)

        personRepository.finn(ident) shouldBe Person(ident, Status.Søkt)
        with(hendelseRepository.finnHendelser(ident).first()) {
            this.ident shouldBe ident
            beskrivelse shouldBe Status.Søkt.name
        }
    }

    @Test
    fun `kan behandle eksisterende person`() {
        val ident = "12345678910"
        val soknadId = "123"

        personRepository.lagre(Person(ident, Avslag))

        personstatusMediator.behandle(ident, soknadId)

        personRepository.finn(ident) shouldBe Person(ident, Status.Søkt)
        with(hendelseRepository.finnHendelser(ident).first()) {
            this.ident shouldBe ident
            beskrivelse shouldBe Status.Søkt.name
        }
    }
}

class PersonRepositoryFaker : PersonRepository {
    private val personliste = mutableMapOf<String, Person>()

    override fun finn(ident: String): Person? = personliste[ident]

    override fun lagre(person: Person) {
        personliste[person.ident] = person
    }

    override fun oppdater(person: Person) {
        personliste.replace(person.ident, person)
    }
}

class HendelseRepositoryFaker : HendelseRepository {
    private val hendelseliste = mutableMapOf<String, Hendelse>()

    override fun finnHendelser(ident: String): List<Hendelse> = hendelseliste.filter { it.key == ident }.map { it.value }

    override fun opprettHendelse(hendelse: Hendelse) {
        hendelseliste[hendelse.ident] = hendelse
    }
}
