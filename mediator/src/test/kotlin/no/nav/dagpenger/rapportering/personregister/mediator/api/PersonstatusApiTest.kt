package no.nav.dagpenger.rapportering.personregister.mediator.api

import io.kotest.matchers.shouldBe
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.every
import no.nav.dagpenger.rapportering.personregister.api.models.AnsvarligSystemResponse
import no.nav.dagpenger.rapportering.personregister.api.models.StatusResponse
import no.nav.dagpenger.rapportering.personregister.mediator.Configuration.defaultObjectMapper
import no.nav.dagpenger.rapportering.personregister.mediator.connector.ArbeidssøkerperiodeResponse
import no.nav.dagpenger.rapportering.personregister.mediator.connector.BrukerResponse
import no.nav.dagpenger.rapportering.personregister.mediator.connector.MetadataResponse
import no.nav.dagpenger.rapportering.personregister.mediator.db.PostgresDataSourceBuilder
import no.nav.dagpenger.rapportering.personregister.mediator.db.PostgresPersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.utils.MetrikkerTestUtil.actionTimer
import no.nav.dagpenger.rapportering.personregister.modell.AnsvarligSystem
import no.nav.dagpenger.rapportering.personregister.modell.Arbeidssøkerperiode
import no.nav.dagpenger.rapportering.personregister.modell.Ident
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.Status
import no.nav.dagpenger.rapportering.personregister.modell.TemporalCollection
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.SøknadHendelse
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.VedtakHendelse
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

class PersonstatusApiTest : ApiTestSetup() {
    private val ident = "12345678910"

    init {
        every { pdlConnector.hentIdenter(ident) } returns listOf(Ident(ident, Ident.IdentGruppe.FOLKEREGISTERIDENT, false))
    }

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
                            OffsetDateTime.now(ZoneOffset.UTC),
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
                    bearerAuth(issueTokenX(ident))
                },
            ) {
                status shouldBe HttpStatusCode.NotFound
            }

            with(
                client.post("/personstatus") {
                    bearerAuth(issueTokenX(ident))
                    setBody(LocalDate.now().format(DateTimeFormatter.ISO_DATE))
                },
            ) {
                status shouldBe HttpStatusCode.OK
            }

            with(
                client.get("/personstatus") {
                    bearerAuth(issueTokenX(ident))
                },
            ) {
                status shouldBe HttpStatusCode.OK
                defaultObjectMapper.readTree(bodyAsText())["ident"].asText() shouldBe ident
                defaultObjectMapper.readTree(bodyAsText())["status"].asText() shouldBe "DAGPENGERBRUKER"
            }
        }

    @Test
    fun `Post personstatus kan få dagpengerbruker = true og false`() =
        setUpTestApplication {
            coEvery { arbeidssøkerConnector.hentSisteArbeidssøkerperiode(eq(ident)) } returns
                listOf(
                    ArbeidssøkerperiodeResponse(
                        UUID.randomUUID(),
                        MetadataResponse(
                            OffsetDateTime.now(ZoneOffset.UTC),
                            BrukerResponse("", ""),
                            "Arena",
                            "Årsak",
                            null,
                        ),
                        null,
                    ),
                )

            // Oppretter bruker
            with(
                client.post("/personstatus") {
                    bearerAuth(issueTokenX(ident))
                    setBody(
                        """
                        {
                          "dagpengerbruker": true,
                          "datoFra": "${LocalDate.now().format(DateTimeFormatter.ISO_DATE)}"
                        }
                        """.trimIndent(),
                    )
                },
            ) {
                status shouldBe HttpStatusCode.OK
            }

            // Bruker fikk en annen meldegruppe
            with(
                client.post("/personstatus") {
                    bearerAuth(issueTokenX(ident))
                    setBody(
                        """
                        {
                          "dagpengerbruker": false,
                          "datoFra": "${LocalDate.now().format(DateTimeFormatter.ISO_DATE)}"
                        }
                        """.trimIndent(),
                    )
                },
            ) {
                status shouldBe HttpStatusCode.OK
            }

            // Bruker må ha status IKKE_DAGPENGERBRUKER nå
            with(
                client.get("/personstatus") {
                    bearerAuth(issueTokenX(ident))
                },
            ) {
                status shouldBe HttpStatusCode.OK
                defaultObjectMapper.readTree(bodyAsText())["ident"].asText() shouldBe ident
                defaultObjectMapper.readTree(bodyAsText())["status"].asText() shouldBe "IKKE_DAGPENGERBRUKER"
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
                    bearerAuth(issueTokenX(ident))
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
                    it.setAnsvarligSystem(AnsvarligSystem.ARENA)
                    personRepository.lagrePerson(it)
                }

            with(
                client.get("/personstatus") {
                    bearerAuth(issueTokenX(ident))
                },
            ) {
                status shouldBe HttpStatusCode.OK
                val obj = defaultObjectMapper.readTree(bodyAsText())
                obj["ident"].asText() shouldBe ident
                obj["status"].asText() shouldBe StatusResponse.IKKE_DAGPENGERBRUKER.value
                obj["overtattBekreftelse"].asBoolean() shouldBe false
                obj["ansvarligSystem"].asText() shouldBe AnsvarligSystemResponse.ARENA.value
            }

            // Får VedtakHendelse
            personRepository
                .hentPerson(ident)
                ?.also { person ->
                    person.behandle(VedtakHendelse(ident, LocalDateTime.now(), LocalDateTime.now(), "ref", "123"))
                    personRepository.oppdaterPerson(person)
                }

            with(
                client.get("/personstatus") {
                    bearerAuth(issueTokenX(ident))
                },
            ) {
                status shouldBe HttpStatusCode.OK
                val obj = defaultObjectMapper.readTree(bodyAsText())
                obj["ident"].asText() shouldBe ident
                obj["status"].asText() shouldBe StatusResponse.IKKE_DAGPENGERBRUKER.value
                obj["overtattBekreftelse"].asBoolean() shouldBe false
                // TODO: obj["ansvarligSystem"].asText() shouldBe AnsvarligSystemResponse.DP.value når vi har dp-meldekortregister
                obj["ansvarligSystem"].asText() shouldBe AnsvarligSystemResponse.ARENA.value
            }

            // Oppdaterer Status og AnsvarligSystem
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
                    2,
                ).also { it.setAnsvarligSystem(AnsvarligSystem.DP) },
            )

            with(
                client.get("/personstatus") {
                    bearerAuth(issueTokenX(ident))
                },
            ) {
                status shouldBe HttpStatusCode.OK
                val obj = defaultObjectMapper.readTree(bodyAsText())
                obj["ident"].asText() shouldBe ident
                obj["status"].asText() shouldBe StatusResponse.DAGPENGERBRUKER.value
                obj["overtattBekreftelse"].asBoolean() shouldBe true
                obj["ansvarligSystem"].asText() shouldBe AnsvarligSystemResponse.DP.value
            }
        }
}

fun lagHendelse(ident: String) =
    SøknadHendelse(
        ident = ident,
        referanseId = "123",
        dato = LocalDateTime.now(),
        startDato = LocalDateTime.now(),
    )
