package no.nav.dagpenger.rapportering.personregister.mediator.api

import io.kotest.matchers.shouldBe
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.mockk.every
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
        every { pdlConnector.hentIdenter(ident) } returns
            listOf(
                Ident(
                    ident,
                    Ident.IdentGruppe.FOLKEREGISTERIDENT,
                    false,
                ),
            )
    }

    @Test
    fun `Post person uten token gir unauthorized`() =
        setUpTestApplication {
            with(client.post("/person")) {
                status shouldBe HttpStatusCode.Unauthorized
            }
        }

    @Test
    fun `Post person gir bad request hvis idenr ikke er gyldig`() =
        setUpTestApplication {
            with(
                client.post("/person") {
                    header(HttpHeaders.Authorization, "Bearer ${issueAzureAdToken(emptyMap())}")
                    setBody("hei")
                },
            ) {
                status shouldBe HttpStatusCode.BadRequest
            }
        }

    @Test
    fun `Post person gir not found hvis personen ikke finnes`() =
        setUpTestApplication {
            with(
                client.post("/person") {
                    header(HttpHeaders.Authorization, "Bearer ${issueAzureAdToken(emptyMap())}")
                    setBody(ident)
                },
            ) {
                status shouldBe HttpStatusCode.NotFound
            }
        }

    @Test
    fun `Post person returnerer personId hvis den finnes`() =
        setUpTestApplication {
            val personRepository = PostgresPersonRepository(PostgresDataSourceBuilder.dataSource, actionTimer)

            Person(ident)
                .apply { behandle(lagHendelse(ident)) }
                .also {
                    personRepository.lagrePerson(it)
                }

            with(
                client.post("/person") {
                    header(HttpHeaders.Authorization, "Bearer ${issueAzureAdToken(emptyMap())}")
                    setBody(ident)
                },
            ) {
                status shouldBe HttpStatusCode.OK
                bodyAsText() shouldBe "1"
            }
        }
}
