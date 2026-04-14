package no.nav.dagpenger.rapportering.personregister.mediator.api

import io.kotest.matchers.shouldBe
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import no.nav.dagpenger.rapportering.personregister.mediator.Configuration.defaultObjectMapper
import no.nav.dagpenger.rapportering.personregister.mediator.connector.MeldekortResponse
import org.junit.jupiter.api.Test
import java.time.LocalDate

class MeldekortStatusApiTest : ApiTestSetup() {
    private val ident = "12345678901"
    private val idag = LocalDate.now()

    @Test
    fun `uten token gir 401`() =
        setUpTestApplication {
            client.get("/meldekort/status").status shouldBe HttpStatusCode.Unauthorized
        }

    @Test
    fun `har innsendte og meldekort til utfylling`() =
        setUpTestApplication {
            coEvery { meldekortregisterConnector.hentMeldekort(ident) } returns
                listOf(
                    innsendtMeldekort(),
                    meldekortTilUtfylling(kanSendesFra = idag, sisteFristForTrekk = idag.plusDays(7)),
                )

            with(client.get("/meldekort/status") { bearerAuth(issueTokenX(ident)) }) {
                status shouldBe HttpStatusCode.OK
                val body = defaultObjectMapper.readTree(bodyAsText())
                body["harInnsendteMeldekort"].asBoolean() shouldBe true
                body["meldekortTilUtfylling"].size() shouldBe 1
                body["redirectUrl"].asText() shouldBe "/arbeid/dagpenger/meldekort"
            }
        }

    @Test
    fun `har innsendte men ingen meldekort til utfylling`() =
        setUpTestApplication {
            coEvery { meldekortregisterConnector.hentMeldekort(ident) } returns listOf(innsendtMeldekort())

            with(client.get("/meldekort/status") { bearerAuth(issueTokenX(ident)) }) {
                status shouldBe HttpStatusCode.OK
                val body = defaultObjectMapper.readTree(bodyAsText())
                body["harInnsendteMeldekort"].asBoolean() shouldBe true
                body["meldekortTilUtfylling"].size() shouldBe 0
                body["redirectUrl"].asText() shouldBe "/arbeid/dagpenger/meldekort"
            }
        }

    @Test
    fun `ingen meldekort gir tom respons`() =
        setUpTestApplication {
            coEvery { meldekortregisterConnector.hentMeldekort(ident) } returns emptyList()

            with(client.get("/meldekort/status") { bearerAuth(issueTokenX(ident)) }) {
                status shouldBe HttpStatusCode.OK
                val body = defaultObjectMapper.readTree(bodyAsText())
                body["harInnsendteMeldekort"].asBoolean() shouldBe false
                body["meldekortTilUtfylling"].size() shouldBe 0
                body["redirectUrl"].asText() shouldBe ""
            }
        }

    @Test
    fun `datofelter mappes til riktig format`() =
        setUpTestApplication {
            coEvery { meldekortregisterConnector.hentMeldekort(ident) } returns
                listOf(meldekortTilUtfylling(kanSendesFra = idag, sisteFristForTrekk = idag.plusDays(13)))

            with(client.get("/meldekort/status") { bearerAuth(issueTokenX(ident)) }) {
                status shouldBe HttpStatusCode.OK
                val meldekort = defaultObjectMapper.readTree(bodyAsText())["meldekortTilUtfylling"][0]
                meldekort["kanSendesFra"].asText() shouldBe "${idag}T00:00:00Z"
                meldekort["fristForInnsending"].asText() shouldBe "${idag.plusDays(13)}T00:00:00Z"
            }
        }

    private fun innsendtMeldekort() =
        MeldekortResponse(
            status = "Innsendt",
            kanSendesFra = idag.minusDays(14),
            sisteFristForTrekk = idag.minusDays(7),
        )

    private fun meldekortTilUtfylling(
        kanSendesFra: LocalDate,
        sisteFristForTrekk: LocalDate,
    ) = MeldekortResponse(
        status = "TilUtfylling",
        kanSendesFra = kanSendesFra,
        sisteFristForTrekk = sisteFristForTrekk,
    )
}
