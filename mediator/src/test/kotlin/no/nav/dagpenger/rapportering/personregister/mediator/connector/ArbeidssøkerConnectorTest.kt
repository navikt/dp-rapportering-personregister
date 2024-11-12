package no.nav.dagpenger.rapportering.personregister.mediator.connector

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.util.UUID

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
    fun `Kan mappe fult objekt`() {
        val periodeId = UUID.randomUUID()
        val response =
            runBlocking {
                arbeidssøkerConnector(arbeidssøkerResponse(periodeId), 200).hentSisteArbeidssøkerperiode("12345678901")
            }

        response.size shouldBe 1
        with(response[0]) {
            periodeId.toString() shouldBe periodeId.toString()
            avsluttet shouldNotBe null
        }
    }

    @Test
    fun `Kan mappe objekt uten avsluttet`() {
        val periodeId = UUID.randomUUID()
        val response =
            runBlocking {
                arbeidssøkerConnector(
                    arbeidssøkerResponse(periodeId, inkluderAvsluttet = false),
                    200,
                ).hentSisteArbeidssøkerperiode("12345678901")
            }

        response.size shouldBe 1
        with(response[0]) {
            periodeId.toString() shouldBe periodeId.toString()
            avsluttet shouldBe null
        }
    }

    @Test
    fun `Kan håndtere tom liste`() {
        val response =
            runBlocking {
                arbeidssøkerConnector(
                    """[]""",
                    200,
                ).hentSisteArbeidssøkerperiode("12345678901")
            }

        response.size shouldBe 0
    }

    @Test
    fun `Kaster exception hvis response status ikke er 200`() {
        shouldThrow<RuntimeException> {
            runBlocking {
                arbeidssøkerConnector(
                    """{feilkode: "400", melding: "Bad request error"}""",
                    400,
                ).hentSisteArbeidssøkerperiode("12345678901")
            }
        }
    }
}

fun arbeidssøkerResponse(
    periodeId: UUID,
    inkluderAvsluttet: Boolean = true,
) = """
    [
      {
        "periodeId": "$periodeId",
        "startet": {
          "tidspunkt": "2024-11-01T10:36:48.045Z",
          "utfoertAv": { "type": "SLUTTBRUKER", "id": "12345678901" },
          "kilde": "europe-north1-docker.pkg.dev/nais-management-233d/paw/paw-arbeidssokerregisteret-api-inngang:24.11.01.127-1",
          "aarsak": "Er over 18 år, er bosatt i Norge i henhold Folkeregisterloven",
          "tidspunktFraKilde": null
        }
        ${if (inkluderAvsluttet) {
    """
        ,
        "avsluttet": {
        "tidspunkt": "2024-11-04T11:20:33.372Z",
        "utfoertAv": {
            "type": "SYSTEM",
            "id": "europe-north1-docker.pkg.dev/nais-management-233d/paw/paw-arbeidssoekerregisteret-bekreftelse-utgang:24.11.01.38-1"
        },
        "kilde": "paw.arbeidssoekerregisteret.bekreftelse-utgang",
        "aarsak": "Graceperiode utløpt",
        "tidspunktFraKilde": null
    }
    """.trimIndent()
} else {
    ""
}}
      }
    ]
    """.trimIndent()
