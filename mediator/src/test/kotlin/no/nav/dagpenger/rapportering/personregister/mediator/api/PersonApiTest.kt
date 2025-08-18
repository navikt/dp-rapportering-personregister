package no.nav.dagpenger.rapportering.personregister.mediator.api

import io.kotest.matchers.shouldBe
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.mockk.every
import no.nav.dagpenger.rapportering.personregister.mediator.db.PostgresDataSourceBuilder
import no.nav.dagpenger.rapportering.personregister.mediator.db.PostgresPersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.utils.MetrikkerTestUtil.actionTimer
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
    fun `hentPersonId uten token gir unauthorized`() =
        setUpTestApplication {
            with(client.post("/hentPersonId")) {
                status shouldBe HttpStatusCode.Unauthorized
            }
        }

    @Test
    fun `hentPersonId gir bad request hvis ident ikke er gyldig`() =
        setUpTestApplication {
            with(
                client.post("/hentPersonId") {
                    bearerAuth(issueAzureAdToken(emptyMap()))
                    setBody("hei")
                },
            ) {
                status shouldBe HttpStatusCode.BadRequest
            }
        }

    @Test
    fun `hentPersonId gir not found hvis personen ikke finnes`() =
        setUpTestApplication {
            with(
                client.post("/hentPersonId") {
                    bearerAuth(issueAzureAdToken(emptyMap()))
                    setBody(ident)
                },
            ) {
                status shouldBe HttpStatusCode.NotFound
            }
        }

    @Test
    fun `hentPersonId returnerer personId hvis den finnes`() =
        setUpTestApplication {
            val personRepository = PostgresPersonRepository(PostgresDataSourceBuilder.dataSource, actionTimer)

            Person(ident)
                .apply { behandle(lagHendelse(ident)) }
                .also {
                    personRepository.lagrePerson(it)
                }

            with(
                client.post("/hentPersonId") {
                    bearerAuth(issueAzureAdToken(emptyMap()))
                    setBody(ident)
                },
            ) {
                status shouldBe HttpStatusCode.OK
                bodyAsText() shouldBe "1"
            }
        }

    @Test
    fun `hentIdent uten token gir unauthorized`() =
        setUpTestApplication {
            with(client.post("/hentIdent")) {
                status shouldBe HttpStatusCode.Unauthorized
            }
        }

    @Test
    fun `hentIdent gir bad request hvis ident ikke er gyldig`() =
        setUpTestApplication {
            with(
                client.post("/hentIdent") {
                    bearerAuth(issueAzureAdToken(emptyMap()))
                    setBody("hei")
                },
            ) {
                status shouldBe HttpStatusCode.BadRequest
            }
        }

    @Test
    fun `hentIdent gir not found hvis personen ikke finnes`() =
        setUpTestApplication {
            with(
                client.post("/hentIdent") {
                    bearerAuth(issueAzureAdToken(emptyMap()))
                    setBody("1")
                },
            ) {
                status shouldBe HttpStatusCode.NotFound
            }
        }

    @Test
    fun `hentIdent returnerer ident hvis den finnes`() =
        setUpTestApplication {
            val personRepository = PostgresPersonRepository(PostgresDataSourceBuilder.dataSource, actionTimer)

            Person(ident)
                .apply { behandle(lagHendelse(ident)) }
                .also {
                    personRepository.lagrePerson(it)
                }
            val personId = personRepository.hentPersonId(ident)

            with(
                client.post("/hentIdent") {
                    bearerAuth(issueAzureAdToken(emptyMap()))
                    setBody(personId.toString())
                },
            ) {
                status shouldBe HttpStatusCode.OK
                bodyAsText() shouldBe ident
            }
        }
}
