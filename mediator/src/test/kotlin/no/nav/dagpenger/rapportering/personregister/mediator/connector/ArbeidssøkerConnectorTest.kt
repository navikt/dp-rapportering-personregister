package no.nav.dagpenger.rapportering.personregister.mediator.connector

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class ArbeidssøkerConnectorTest {
    private val arbeidssøkerregisterUrl = "http://arbeidssøkerregister"
    private val testTokenProvider: () -> String = { "testToken" }

    private fun arbeidssøkerConnector(
        responseBody: String,
        statusCode: Int,
    ) = ArbeidssøkerConnector(
        arbeidssøkerregisterUrl,
        testTokenProvider,
        createMockClient(statusCode, responseBody),
    )

    @Test
    fun `Resturnerer tekst`() {
        val responseBody =
            """
            {
                "fom": "2020-01-01",
                "tom": "2020-01-07"
            }
            """.trimIndent()
        val response =
            runBlocking {
                arbeidssøkerConnector(responseBody, 200).hentSisteArbeidssøkerperiode("12345678901")
            }

        response shouldBe responseBody
    }
}
