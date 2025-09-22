package no.nav.dagpenger.rapportering.personregister.mediator.api

import io.kotest.matchers.shouldBe
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import no.nav.dagpenger.rapportering.personregister.mediator.Configuration.defaultObjectMapper
import org.junit.jupiter.api.Test

class BehandlingApiTest : ApiTestSetup() {
    private val ident1 = "12345678911"
    private val ident2 = "12345678912"

    @Test
    fun `hentSisteSakId uten token gir unauthorized`() =
        setUpTestApplication {
            with(client.post("/hentSisteSakId")) {
                status shouldBe HttpStatusCode.Unauthorized
            }
        }

    @Test
    fun `hentSisteSakId gir BadRequest hvis ident ikke er gyldig`() =
        setUpTestApplication {
            with(
                client.post("/hentSisteSakId") {
                    header(HttpHeaders.ContentType, "application/json")
                    bearerAuth(issueAzureAdToken(emptyMap()))
                    setBody(defaultObjectMapper.writeValueAsString(IdentBody("hei")))
                },
            ) {
                status shouldBe HttpStatusCode.BadRequest
            }
        }

    @Test
    fun `hentSisteSakId returnerer sakId hvis den finnes og NoContent hvis sak ikke finnes med AzureAd`() =
        setUpTestApplication {
            with(
                client.post("/hentSisteSakId") {
                    header(HttpHeaders.ContentType, "application/json")
                    bearerAuth(issueAzureAdToken(emptyMap()))
                    setBody(defaultObjectMapper.writeValueAsString(IdentBody(ident1)))
                },
            ) {
                status shouldBe HttpStatusCode.NoContent
            }

            behandlingRepository.lagreData("behandlingId1", "søknadId1", ident1, "sakId1")

            with(
                client.post("/hentSisteSakId") {
                    header(HttpHeaders.ContentType, "application/json")
                    bearerAuth(issueAzureAdToken(emptyMap()))
                    setBody(defaultObjectMapper.writeValueAsString(IdentBody(ident1)))
                },
            ) {
                status shouldBe HttpStatusCode.OK
                defaultObjectMapper.readTree(bodyAsText())["sakId"].asText() shouldBe "sakId1"
            }

            with(
                client.post("/hentSisteSakId") {
                    header(HttpHeaders.ContentType, "application/json")
                    bearerAuth(issueTokenX(ident1))
                    setBody(defaultObjectMapper.writeValueAsString(IdentBody(ident1)))
                },
            ) {
                status shouldBe HttpStatusCode.OK
                defaultObjectMapper.readTree(bodyAsText())["sakId"].asText() shouldBe "sakId1"
            }
        }

    @Test
    fun `hentSisteSakId returnerer sakId hvis den finnes og NoContent hvis sak ikke finnes med TokenX`() =
        setUpTestApplication {
            with(
                client.post("/hentSisteSakId") {
                    header(HttpHeaders.ContentType, "application/json")
                    bearerAuth(issueAzureAdToken(emptyMap()))
                    setBody(defaultObjectMapper.writeValueAsString(IdentBody(ident2)))
                },
            ) {
                status shouldBe HttpStatusCode.NoContent
            }

            behandlingRepository.lagreData("behandlingId2", "søknadId2", ident2, "sakId2")

            with(
                client.post("/hentSisteSakId") {
                    header(HttpHeaders.ContentType, "application/json")
                    bearerAuth(issueTokenX(ident2))
                    setBody(defaultObjectMapper.writeValueAsString(IdentBody(ident2)))
                },
            ) {
                status shouldBe HttpStatusCode.OK
                defaultObjectMapper.readTree(bodyAsText())["sakId"].asText() shouldBe "sakId2"
            }
        }
}
