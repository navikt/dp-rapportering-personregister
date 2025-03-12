package no.nav.dagpenger.rapportering.personregister.mediator.api

import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import no.nav.dagpenger.rapportering.personregister.api.models.StatusResponse
import no.nav.dagpenger.rapportering.personregister.mediator.Configuration.defaultObjectMapper
import no.nav.dagpenger.rapportering.personregister.mediator.connector.ArbeidssøkerperiodeResponse
import no.nav.dagpenger.rapportering.personregister.mediator.connector.BrukerResponse
import no.nav.dagpenger.rapportering.personregister.mediator.connector.MetadataResponse
import no.nav.dagpenger.rapportering.personregister.mediator.db.PostgresDataSourceBuilder
import no.nav.dagpenger.rapportering.personregister.mediator.db.PostgresPersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.utils.MetrikkerTestUtil.actionTimer
import no.nav.dagpenger.rapportering.personregister.modell.Arbeidssøkerperiode
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.Status
import no.nav.dagpenger.rapportering.personregister.modell.SøknadHendelse
import no.nav.dagpenger.rapportering.personregister.modell.TemporalCollection
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

class PersonstatusApiTest : ApiTestSetup() {
    private val ident = "12345678910"

    @Test
    fun `Post personstatus uten token gir unauthorized`() =
        setUpTestApplication {
            with(client.post("/personstatus")) {
                status shouldBe HttpStatusCode.Unauthorized
            }
        }

    @Test
    fun `Post personstatus lagrer person`() =
        setUpTestApplication {
            coEvery { arbeidssøkerConnector.hentSisteArbeidssøkerperiode(eq(ident)) } returns
                listOf(
                    ArbeidssøkerperiodeResponse(
                        UUID.randomUUID(),
                        MetadataResponse(
                            LocalDateTime.now(),
                            BrukerResponse("", ""),
                            "Arena",
                            "Årsak",
                            null,
                        ),
                        null,
                    ),
                )

            with(
                client.get("/personstatus") {
                    header(HttpHeaders.Authorization, "Bearer ${issueToken(ident)}")
                },
            ) {
                status shouldBe HttpStatusCode.NotFound
            }

            with(
                client.post("/personstatus") {
                    header(HttpHeaders.Authorization, "Bearer ${issueToken(ident)}")
                    setBody(LocalDate.now().format(DateTimeFormatter.ISO_DATE))
                },
            ) {
                status shouldBe HttpStatusCode.OK
            }

            with(
                client.get("/personstatus") {
                    header(HttpHeaders.Authorization, "Bearer ${issueToken(ident)}")
                },
            ) {
                status shouldBe HttpStatusCode.OK
                defaultObjectMapper.readTree(bodyAsText())["ident"].asText() shouldBe ident
                defaultObjectMapper.readTree(bodyAsText())["status"].asText() shouldBe "DAGPENGERBRUKER"
            }
        }

    @Test
    fun `Get personstatus uten token gir unauthorized`() =
        setUpTestApplication {
            with(client.get("/personstatus")) {
                status shouldBe HttpStatusCode.Unauthorized
            }
        }

    @Test
    fun `Get personstatus gir not found hvis personen ikke finnes`() =
        setUpTestApplication {
            with(
                client.get("/personstatus") {
                    header(HttpHeaders.Authorization, "Bearer ${issueToken(ident)}")
                },
            ) {
                status shouldBe HttpStatusCode.NotFound
            }
        }

    @Test
    fun `Get personstatus gir personen med riktig status hvis den finnes`() =
        setUpTestApplication {
            val personRepository = PostgresPersonRepository(PostgresDataSourceBuilder.dataSource, actionTimer)

            Person(ident)
                .apply { behandle(lagHendelse(ident)) }
                .also {
                    personRepository.lagrePerson(it)
                }

            with(
                client.get("/personstatus") {
                    header(HttpHeaders.Authorization, "Bearer ${issueToken(ident)}")
                },
            ) {
                status shouldBe HttpStatusCode.OK
                val obj = defaultObjectMapper.readTree(bodyAsText())
                obj["ident"].asText() shouldBe ident
                obj["status"].asText() shouldBe StatusResponse.IKKE_DAGPENGERBRUKER.value
                obj["overtattBekreftelse"].asBoolean() shouldBe false
            }

            personRepository.oppdaterPerson(
                Person(
                    ident,
                    TemporalCollection<Status>().also { it.put(LocalDateTime.now(), Status.DAGPENGERBRUKER) },
                    mutableListOf(
                        Arbeidssøkerperiode(
                            UUID.randomUUID(),
                            ident,
                            LocalDateTime.now(),
                            null,
                            true,
                        ),
                    ),
                ),
            )

            with(
                client.get("/personstatus") {
                    header(HttpHeaders.Authorization, "Bearer ${issueToken(ident)}")
                },
            ) {
                status shouldBe HttpStatusCode.OK
                val obj = defaultObjectMapper.readTree(bodyAsText())
                obj["ident"].asText() shouldBe ident
                obj["status"].asText() shouldBe StatusResponse.DAGPENGERBRUKER.value
                obj["overtattBekreftelse"].asBoolean() shouldBe true
            }
        }
}

fun lagHendelse(ident: String) =
    SøknadHendelse(
        ident = ident,
        referanseId = "123",
        dato = LocalDateTime.now(),
    )
