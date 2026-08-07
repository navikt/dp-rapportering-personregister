package no.nav.dagpenger.rapportering.personregister.mediator.api

import com.fasterxml.jackson.module.kotlin.readValue
import com.github.navikt.tbd_libs.rapids_and_rivers.toUUID
import io.kotest.matchers.shouldBe
import io.ktor.client.request.bearerAuth
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
import java.util.UUID.randomUUID

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
    fun `person-søknad-innsendt-tidspunkt returnerer forventet respons hvis personen eksisterer og personen har søknader`() =
        setUpTestApplication {
            val personRepository = PersonRepositoryPostgres(PostgresDataSourceBuilder.dataSource, actionTimer)
            val søknadHendelse =
                lagSøknadHendelse(
                    ident,
                    UUIDv7.newUuid().toString(),
                    LocalDateTime.of(2026, 5, 17, 23, 59, 59),
                )
            val søknadHendelser =
                listOf(
                    lagSøknadHendelse(
                        ident,
                        UUIDv7.newUuid().toString(),
                        LocalDateTime.of(2025, 12, 31, 0, 0, 0),
                    ),
                    søknadHendelse,
                )
            Person(ident)
                .apply {
                    this.hendelser.addAll(søknadHendelser)
                }.also {
                    personRepository.lagrePerson(it)
                }

            with(
                client.post("/api/person/søknad/innsendt-tidspunkt") {
                    header(HttpHeaders.ContentType, "application/json")
                    bearerAuth(issueAzureAdToken(emptyMap()))
                    setBody(
                        defaultObjectMapper.writeValueAsString(
                            PersonSøknadInnsendtTidspunktRequest(
                                ident,
                                søknadHendelse.referanseId.toUUID(),
                            ),
                        ),
                    )
                },
            ) {
                status shouldBe OK
                defaultObjectMapper.readValue<PersonSøknadInnsendtTidspunktResponse>(bodyAsText()).innsendtTidspunkt shouldBe
                    søknadHendelse.startDato
            }
        }

    @Test
    @Suppress("ktlint:standard:max-line-length")
    fun `person-søknad-innsendt-tidspunkt returnerer forventet respons hvis personen eksisterer men søknaden ikke eksisterer på personen`() =
        setUpTestApplication {
            val søknadId = randomUUID()
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
                        LocalDateTime.of(2025, 12, 31, 0, 0, 0),
                    ),
                )
            Person(ident)
                .apply {
                    this.hendelser.addAll(søknadHendelser)
                }.also {
                    personRepository.lagrePerson(it)
                }

            with(
                client.post("/api/person/søknad/innsendt-tidspunkt") {
                    header(HttpHeaders.ContentType, "application/json")
                    bearerAuth(issueAzureAdToken(emptyMap()))
                    setBody(
                        defaultObjectMapper.writeValueAsString(
                            PersonSøknadInnsendtTidspunktRequest(
                                ident,
                                søknadId,
                            ),
                        ),
                    )
                },
            ) {
                status shouldBe NotFound
                with(defaultObjectMapper.readValue<HttpProblem>(bodyAsText())) {
                    detail shouldBe "Fant ikke søknadId=$søknadId"
                }
            }
        }

    @Test
    fun `person-søknad-innsendt-tidspunkt returnerer forventet respons hvis personen ikke eksisterer`() =
        setUpTestApplication {
            with(
                client.post("/api/person/søknad/innsendt-tidspunkt") {
                    header(HttpHeaders.ContentType, "application/json")
                    bearerAuth(issueAzureAdToken(emptyMap()))
                    setBody(
                        defaultObjectMapper.writeValueAsString(
                            PersonSøknadInnsendtTidspunktRequest(
                                ident,
                                randomUUID(),
                            ),
                        ),
                    )
                },
            ) {
                status shouldBe NotFound
                with(defaultObjectMapper.readValue<HttpProblem>(bodyAsText())) {
                    detail shouldBe "Finner ikke person"
                }
            }
        }

    @Test
    fun `person-søknad-innsendt-tidspunkt returnerer forventet respons hvis ident ikke validerer`() =
        setUpTestApplication {
            with(
                client.post("/api/person/søknad/innsendt-tidspunkt") {
                    header(HttpHeaders.ContentType, "application/json")
                    bearerAuth(issueAzureAdToken(emptyMap()))
                    setBody(
                        defaultObjectMapper.writeValueAsString(
                            PersonSøknadInnsendtTidspunktRequest(
                                "ident-som-ikke-validerer",
                                randomUUID(),
                            ),
                        ),
                    )
                },
            ) {
                status shouldBe BadRequest
            }
        }
}
