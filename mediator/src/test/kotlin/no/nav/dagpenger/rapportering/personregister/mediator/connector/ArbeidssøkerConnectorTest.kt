package no.nav.dagpenger.rapportering.personregister.mediator.connector

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.rapportering.personregister.mediator.Configuration.defaultObjectMapper
import no.nav.dagpenger.rapportering.personregister.mediator.utils.MetrikkerTestUtil.actionTimer
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.random.Random

class ArbeidssøkerConnectorTest {
    private val arbeidssøkerregisterOppslagUrl = "http://arbeidssøkerregister-oppslag"
    private val arbeidssokerregisterRecordKeyUrl = "http://arbeidssøkerregister-record-key"
    private val testTokenProvider: () -> String = { "testToken" }

    init {
        System.setProperty("KAFKA_SCHEMA_REGISTRY", "KAFKA_SCHEMA_REGISTRY")
        System.setProperty("KAFKA_SCHEMA_REGISTRY_USER", "KAFKA_SCHEMA_REGISTRY_USER")
        System.setProperty("KAFKA_SCHEMA_REGISTRY_PASSWORD", "KAFKA_SCHEMA_REGISTRY_PASSWORD")
        System.setProperty("KAFKA_BROKERS", "KAFKA_BROKERS")
    }

    private fun arbeidssøkerConnector(
        responseBody: String,
        statusCode: Int,
    ) = ArbeidssøkerConnector(
        arbeidssøkerregisterOppslagUrl,
        testTokenProvider,
        arbeidssokerregisterRecordKeyUrl,
        testTokenProvider,
        createMockClient(statusCode, responseBody),
        actionTimer,
    )

    @Test
    fun `Oppslag - Kan mappe fult objekt`() {
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
    fun `Oppslag - Kan mappe objekt uten avsluttet`() {
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
    fun `Oppslag - Kan håndtere tom liste`() {
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
    fun `Oppslag - Kaster exception hvis response status ikke er 200`() {
        shouldThrow<RuntimeException> {
            runBlocking {
                arbeidssøkerConnector(
                    """{feilkode: "400", melding: "Bad request error"}""",
                    400,
                ).hentSisteArbeidssøkerperiode("12345678901")
            }
        }
    }

    @Test
    fun `Record key - Kan mappe fult objekt`() {
        val response =
            runBlocking {
                arbeidssøkerConnector(recordKeyResponse(), 200).hentRecordKey("12345678901")
            }

        response shouldNotBe null
    }

    @Test
    fun `Record key - Kaster exception hvis response status ikke er 200`() {
        shouldThrow<RuntimeException> {
            runBlocking {
                arbeidssøkerConnector(
                    """{feilkode: "400", melding: "Bad request error"}""",
                    400,
                ).hentRecordKey("12345678901")
            }
        }
    }
}

fun recordKeyResponse() =
    defaultObjectMapper
        .writeValueAsString(
            RecordKeyResponse(
                key = Random.nextLong(),
            ),
        )

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
                        tidspunkt = OffsetDateTime.now(ZoneOffset.UTC).minusWeeks(3),
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
                            tidspunkt = OffsetDateTime.now(ZoneOffset.UTC).minusDays(2),
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
