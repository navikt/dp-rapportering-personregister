package no.nav.dagpenger.rapportering.personregister.mediator.connector

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.rapportering.personregister.mediator.Configuration.defaultObjectMapper
import no.nav.dagpenger.rapportering.personregister.modell.arbeidssøker.ArbeidssøkerperiodeResponse
import no.nav.dagpenger.rapportering.personregister.modell.arbeidssøker.BrukerResponse
import no.nav.dagpenger.rapportering.personregister.modell.arbeidssøker.MetadataResponse
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
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
) = defaultObjectMapper
    .writeValueAsString(
        listOf(
            ArbeidssøkerperiodeResponse(
                periodeId = periodeId,
                startet =
                    MetadataResponse(
                        tidspunkt = LocalDateTime.now().minusWeeks(3),
                        utfoertAv =
                            BrukerResponse(
                                type = "SLUTTBRUKER",
                                id = "12345678910",
                            ),
                        kilde = "kilde",
                        aarsak = "aarsak",
                        tidspunktFraKilde = null,
                    ),
                avsluttet =
                    if (inkluderAvsluttet) {
                        MetadataResponse(
                            tidspunkt = LocalDateTime.now().minusDays(2),
                            utfoertAv =
                                BrukerResponse(
                                    type = "SYSTEM",
                                    id = "paw-arbeidssoekerregisteret-bekreftelse-utgang:24.11.01.38-1",
                                ),
                            kilde = "kilde",
                            aarsak = "Graceperiode utløpt",
                            tidspunktFraKilde = null,
                        )
                    } else {
                        null
                    },
            ),
        ),
    )
