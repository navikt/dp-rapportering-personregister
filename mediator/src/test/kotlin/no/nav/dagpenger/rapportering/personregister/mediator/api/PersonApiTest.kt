package no.nav.dagpenger.rapportering.personregister.mediator.api

import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import no.nav.dagpenger.pdl.PDLPerson
import no.nav.dagpenger.rapportering.personregister.mediator.Configuration.defaultObjectMapper
import no.nav.dagpenger.rapportering.personregister.mediator.db.PostgresDataSourceBuilder
import no.nav.dagpenger.rapportering.personregister.mediator.db.PostgresPersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.utils.MetrikkerTestUtil.actionTimer
import no.nav.dagpenger.rapportering.personregister.modell.AnsvarligSystem
import no.nav.dagpenger.rapportering.personregister.modell.Ident
import no.nav.dagpenger.rapportering.personregister.modell.Person
import org.junit.jupiter.api.Test

class PersonApiTest : ApiTestSetup() {
    private val ident = "12345678910"

    init {
        every { pdlConnector.hentIdenter(ident) } returns listOf(Ident(ident, Ident.IdentGruppe.FOLKEREGISTERIDENT, false))
    }

    @Test
    fun `Get person uten token gir unauthorized`() =
        setUpTestApplication {
            with(client.get("/person/1")) {
                status shouldBe HttpStatusCode.Unauthorized
            }
        }

    @Test
    fun `Get person gir not found hvis personen ikke finnes`() =
        setUpTestApplication {
            with(
                client.get("/person/1") {
                    header(HttpHeaders.Authorization, "Bearer ${issueAzureAdToken(emptyMap())}")
                },
            ) {
                status shouldBe HttpStatusCode.NotFound
            }
        }

    @Test
    fun `Get person gir not found hvis personen ikke finnes i PDL`() =
        setUpTestApplication {
            coEvery { pdlConnector.hentPerson(ident) } returns null

            with(
                client.get("/person/1") {
                    header(HttpHeaders.Authorization, "Bearer ${issueAzureAdToken(emptyMap())}")
                },
            ) {
                status shouldBe HttpStatusCode.NotFound
            }
        }

    @Test
    fun `Get person gir personen med riktig data hvis den finnes`() =
        setUpTestApplication {
            val fornavn = "Test"
            val etternavn = "Testesen"
            val mellomnavn = ""
            val statsborgerskap = "NOR"

            val pdlPerson = mockk<PDLPerson>()
            every { pdlPerson.fornavn } returns fornavn
            every { pdlPerson.etternavn } returns etternavn
            every { pdlPerson.mellomnavn } returns mellomnavn
            every { pdlPerson.statsborgerskap } returns statsborgerskap
            coEvery { pdlConnector.hentPerson(ident) } returns pdlPerson

            val personRepository = PostgresPersonRepository(PostgresDataSourceBuilder.dataSource, actionTimer)

            Person(ident)
                .apply { behandle(lagHendelse(ident)) }
                .also {
                    it.setAnsvarligSystem(AnsvarligSystem.ARENA)
                    personRepository.lagrePerson(it)
                }

            with(
                client.get("/person/1") {
                    header(HttpHeaders.Authorization, "Bearer ${issueAzureAdToken(emptyMap())}")
                },
            ) {
                status shouldBe HttpStatusCode.OK
                val obj = defaultObjectMapper.readTree(bodyAsText())
                obj["ident"].asText() shouldBe ident
                obj["fornavn"].asText() shouldBe fornavn
                obj["etternavn"].asText() shouldBe etternavn
                obj["mellomnavn"].asText() shouldBe mellomnavn
                obj["statsborgerskap"].asText() shouldBe statsborgerskap
            }
        }
}
