package no.nav.dagpenger.rapportering.personregister.mediator

import io.kotest.matchers.shouldBe
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.modell.Hendelse
import no.nav.dagpenger.rapportering.personregister.modell.Kildesystem
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.Status
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class PersonstatusMediatorTest {
    private lateinit var rapidsConnection: TestRapid
    private lateinit var personRepository: PersonRepository
    private lateinit var personstatusMediator: PersonstatusMediator

    @BeforeEach
    fun setup() {
        rapidsConnection = TestRapid()
        personRepository = PersonRepositoryFaker()
        personstatusMediator = PersonstatusMediator(rapidsConnection, personRepository)
    }

    @Test
    fun `kan behandle en ny hendelse med ny person`() {
        val ident = "12345678910"
        val soknadId = "123"

        personRepository.finn(ident) shouldBe null

        personstatusMediator
            .behandle(ident, soknadId)

        personRepository.finn(ident)?.apply {
            ident shouldBe ident
            status shouldBe Status.Søkt
        }
    }

    @Test
    fun `kan behandle eksisterende person`() {
        val ident = "12345678910"
        val soknadId = "123"

        val hendelse =
            Hendelse(
                ident = ident,
                referanseId = soknadId,
                dato = LocalDateTime.now(),
                status = Status.Søkt,
                kilde = Kildesystem.DpSoknad,
            )

        val person = Person(ident, hendelse)
        personRepository.lagre(person)

        personstatusMediator.behandle(ident, soknadId)

        personRepository.finn(ident)?.apply {
            ident shouldBe ident
            status shouldBe Status.Søkt
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
