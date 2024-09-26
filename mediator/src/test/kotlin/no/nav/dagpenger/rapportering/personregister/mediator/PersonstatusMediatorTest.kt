package no.nav.dagpenger.rapportering.personregister.mediator

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.matchers.shouldBe
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.hendelser.SøknadHendelse
import no.nav.dagpenger.rapportering.personregister.mediator.hendelser.VedtakHendelse
import no.nav.dagpenger.rapportering.personregister.modell.Hendelse
import no.nav.dagpenger.rapportering.personregister.modell.Kildesystem
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.Status
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
        personstatusMediator = PersonstatusMediator(personRepository)
    }

    @Test
    fun `kan behandle en ny hendelse med ny person`() {
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
            status shouldBe Status.Søkt
        }
    }

    @Test
    fun `kan behandle eksisterende person`() {
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
                status = Status.Søkt,
                kilde = Kildesystem.Søknad,
            )

        val person = Person(ident).apply { behandle(hendelse) }
        personRepository.lagrePerson(person)

        personstatusMediator.behandle(søknadHendelse)

        personRepository.hentPerson(ident)?.apply {
            ident shouldBe ident
            status shouldBe Status.Søkt
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
                status = Status.Innvilget,
            ),
        )

        personRepository.hentPerson(ident)?.apply {
            ident shouldBe ident
            status shouldBe Status.Innvilget
        }
    }

    @Test
    fun `kan behandle vedtak hendelse for eksisterende person`() {
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
                status = Status.Innvilget,
            ),
        )

        personRepository.hentPerson(ident)?.apply {
            ident shouldBe ident
            status shouldBe Status.Innvilget
            status(dato) shouldBe Status.Søkt
        }
    }
}

class PersonRepositoryFaker : PersonRepository {
    private val personliste = mutableMapOf<String, Person>()

    override fun hentPerson(ident: String): Person? = personliste[ident]

    override fun lagrePerson(person: Person) {
        personliste[person.ident] = person
    }

    override fun oppdaterPerson(person: Person) {
        personliste.replace(person.ident, person)
    }
}
