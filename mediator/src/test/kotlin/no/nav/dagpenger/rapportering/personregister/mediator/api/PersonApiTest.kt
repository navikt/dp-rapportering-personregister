package no.nav.dagpenger.rapportering.personregister.mediator.api

import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.matchers.collections.shouldContainOnly
import io.kotest.matchers.shouldBe
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.mockk.every
import no.nav.dagpenger.rapportering.personregister.api.models.SoknadResponse
import no.nav.dagpenger.rapportering.personregister.mediator.Configuration.defaultObjectMapper
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepositoryPostgres
import no.nav.dagpenger.rapportering.personregister.mediator.db.PostgresDataSourceBuilder
import no.nav.dagpenger.rapportering.personregister.mediator.lagSøknadHendelse
import no.nav.dagpenger.rapportering.personregister.mediator.utils.MetrikkerTestUtil.actionTimer
import no.nav.dagpenger.rapportering.personregister.mediator.utils.UUIDv7
import no.nav.dagpenger.rapportering.personregister.modell.Ident
import no.nav.dagpenger.rapportering.personregister.modell.Person
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

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
                    header(HttpHeaders.ContentType, "application/json")
                    bearerAuth(issueAzureAdToken(emptyMap()))
                    setBody(defaultObjectMapper.writeValueAsString(IdentBody("hei")))
                },
            ) {
                status shouldBe BadRequest
            }
        }

    @Test
    fun `hentPersonId gir not found hvis personen ikke finnes`() =
        setUpTestApplication {
            with(
                client.post("/hentPersonId") {
                    header(HttpHeaders.ContentType, "application/json")
                    bearerAuth(issueAzureAdToken(emptyMap()))
                    setBody(defaultObjectMapper.writeValueAsString(IdentBody(ident)))
                },
            ) {
                status shouldBe NotFound
            }
        }

    @Test
    fun `hentPersonId returnerer personId hvis den finnes`() =
        setUpTestApplication {
            val personRepository = PersonRepositoryPostgres(PostgresDataSourceBuilder.dataSource, actionTimer)

            Person(ident)
                .apply { behandle(lagSøknadHendelse(ident)) }
                .also {
                    personRepository.lagrePerson(it)
                }

            with(
                client.post("/hentPersonId") {
                    header(HttpHeaders.ContentType, "application/json")
                    bearerAuth(issueAzureAdToken(emptyMap()))
                    setBody(defaultObjectMapper.writeValueAsString(IdentBody(ident)))
                },
            ) {
                status shouldBe OK
                defaultObjectMapper.readTree(bodyAsText())["personId"].asText() shouldBe "1"
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
                    header(HttpHeaders.ContentType, "application/json")
                    bearerAuth(issueAzureAdToken(emptyMap()))
                    setBody("{ personId: 'hei' }")
                },
            ) {
                status shouldBe BadRequest
            }
        }

    @Test
    fun `hentIdent gir not found hvis personen ikke finnes`() =
        setUpTestApplication {
            with(
                client.post("/hentIdent") {
                    header(HttpHeaders.ContentType, "application/json")
                    bearerAuth(issueAzureAdToken(emptyMap()))
                    setBody(defaultObjectMapper.writeValueAsString(PersonIdBody(1)))
                },
            ) {
                status shouldBe NotFound
            }
        }

    @Test
    fun `hentIdent returnerer ident hvis den finnes`() =
        setUpTestApplication {
            val personRepository = PersonRepositoryPostgres(PostgresDataSourceBuilder.dataSource, actionTimer)

            Person(ident)
                .apply { behandle(lagSøknadHendelse(ident)) }
                .also {
                    personRepository.lagrePerson(it)
                }
            val personId = personRepository.hentPersonId(ident)!!

            with(
                client.post("/hentIdent") {
                    header(HttpHeaders.ContentType, "application/json")
                    bearerAuth(issueAzureAdToken(emptyMap()))
                    setBody(defaultObjectMapper.writeValueAsString(PersonIdBody(personId)))
                },
            ) {
                status shouldBe OK
                defaultObjectMapper.readTree(bodyAsText())["ident"].asText() shouldBe ident
            }
        }

    @Test
    fun `person-{personId}-søknader returnerer forventet respons hvis personen eksisterer og personen har søknader`() =
        setUpTestApplication {
            val personRepository = PersonRepositoryPostgres(PostgresDataSourceBuilder.dataSource, actionTimer)
            val søknadHendelser =
                listOf(
                    lagSøknadHendelse(
                        ident,
                        UUIDv7.newUuid().toString(),
                        LocalDateTime.of(2025, 12, 31, 0, 0, 0),
                    ),
                    lagSøknadHendelse(
                        ident,
                        UUIDv7.newUuid().toString(),
                        LocalDateTime.of(2026, 5, 17, 23, 59, 59),
                    ),
                )
            Person(ident)
                .apply {
                    this.hendelser.addAll(søknadHendelser)
                }.also {
                    personRepository.lagrePerson(it)
                }
            val personId = personRepository.hentPersonId(ident)!!

            with(
                client.get("/api/person/$personId/søknader") {
                    header(HttpHeaders.ContentType, "application/json")
                    bearerAuth(issueAzureAdToken(emptyMap()))
                },
            ) {
                status shouldBe OK
                with(defaultObjectMapper.readValue<List<SoknadResponse>>(bodyAsText())) {
                    isNotEmpty() shouldBe true
                    this shouldContainOnly
                        listOf(
                            SoknadResponse(
                                søknadHendelser[0].referanseId,
                                søknadHendelser[0].startDato.toString(),
                            ),
                            SoknadResponse(
                                søknadHendelser[1].referanseId,
                                søknadHendelser[1].startDato.toString(),
                            ),
                        )
                }
            }
        }

    @Test
    fun `person-{personId}-søknader returnerer forventet respons hvis personen eksisterer men personen ikke har søknader`() =
        setUpTestApplication {
            val personRepository = PersonRepositoryPostgres(PostgresDataSourceBuilder.dataSource, actionTimer)
            Person(ident)
                .also {
                    personRepository.lagrePerson(it)
                }
            val personId = personRepository.hentPersonId(ident)!!

            with(
                client.get("/api/person/$personId/søknader") {
                    header(HttpHeaders.ContentType, "application/json")
                    bearerAuth(issueAzureAdToken(emptyMap()))
                },
            ) {
                status shouldBe OK
                defaultObjectMapper.readValue<List<SoknadResponse>>(bodyAsText()).isEmpty() shouldBe true
            }
        }

    @Test
    fun `person-{personId}-søknader returnerer forventet respons hvis personen ikke eksisterer`() =
        setUpTestApplication {
            with(
                client.get("/api/person/123456/søknader") {
                    header(HttpHeaders.ContentType, "application/json")
                    bearerAuth(issueAzureAdToken(emptyMap()))
                },
            ) {
                status shouldBe NotFound
            }
        }

    @Test
    fun `person-{personId}-søknader returnerer forventet respons hvis personId er ugyldig`() =
        setUpTestApplication {
            with(
                client.get("/api/person/hei-jeg-er-ikke-en-gyldig-personId/søknader") {
                    header(HttpHeaders.ContentType, "application/json")
                    bearerAuth(issueAzureAdToken(emptyMap()))
                },
            ) {
                status shouldBe BadRequest
            }
        }
}
