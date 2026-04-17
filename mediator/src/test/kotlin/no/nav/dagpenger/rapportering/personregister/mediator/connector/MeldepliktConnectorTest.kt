package no.nav.dagpenger.rapportering.personregister.mediator.connector

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.rapportering.personregister.mediator.Configuration.defaultObjectMapper
import no.nav.dagpenger.rapportering.personregister.mediator.utils.MetrikkerTestUtil.actionTimer
import no.nav.dagpenger.rapportering.personregister.modell.meldestatus.MeldestatusResponse
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class MeldepliktConnectorTest {
    private val meldepliktAdapterUrl = "http://meldeplikt-adapter"
    private val testTokenProvider: () -> String = { "testToken" }

    init {
        System.setProperty("KAFKA_SCHEMA_REGISTRY", "KAFKA_SCHEMA_REGISTRY")
        System.setProperty("KAFKA_SCHEMA_REGISTRY_USER", "KAFKA_SCHEMA_REGISTRY_USER")
        System.setProperty("KAFKA_SCHEMA_REGISTRY_PASSWORD", "KAFKA_SCHEMA_REGISTRY_PASSWORD")
        System.setProperty("KAFKA_BROKERS", "KAFKA_BROKERS")
    }

    private fun meldepliktConnector(
        responseBody: String,
        statusCode: Int,
    ) = MeldepliktConnector(
        meldepliktAdapterUrl = meldepliktAdapterUrl,
        meldepliktAdapterTokenProvider = testTokenProvider,
        httpClient = createMockClient(statusCode, responseBody),
        actionTimer = actionTimer,
    )

    @Test
    fun `Kan mappe respons fra hentMeldeplikt`() {
        val response = runBlocking { meldepliktConnector("true", 200).hentMeldeplikt("12345678901") }

        response shouldBe true
    }

    @Test
    fun `Feilstatus fra hentMeldeplikt trigger RuntimeException`() {
        shouldThrow<RuntimeException> {
            runBlocking {
                meldepliktConnector("false", 500).hentMeldeplikt("12345678901")
            }
        }
    }

    @Test
    fun `Kan mappe respons fra hentMeldestatus`() {
        val meldestatusResponse =
            MeldestatusResponse(
                1L,
                "12345678901",
                "DAGP",
                true,
                listOf(
                    MeldestatusResponse.Meldeplikt(
                        true,
                        MeldestatusResponse.Periode(
                            LocalDateTime.now().minusDays(10),
                        ),
                        "",
                        MeldestatusResponse.Endring(
                            "R123456",
                            LocalDateTime.now().minusDays(7),
                            "E654321",
                            LocalDateTime.now(),
                        ),
                    ),
                ),
                listOf(
                    MeldestatusResponse.Meldegruppe(
                        "ATTF",
                        MeldestatusResponse.Periode(
                            LocalDateTime.now().minusDays(10),
                        ),
                        "Bla bla",
                        MeldestatusResponse.Endring(
                            "R123456",
                            LocalDateTime.now().minusDays(7),
                            "E654321",
                            LocalDateTime.now(),
                        ),
                    ),
                ),
            )

        val response =
            runBlocking {
                meldepliktConnector(
                    defaultObjectMapper.writeValueAsString(meldestatusResponse),
                    200,
                ).hentMeldestatus(1L)
            }

        response shouldBe meldestatusResponse
    }

    @Test
    fun `Feilstatus fra hentMeldestatus trigger RuntimeException`() {
        shouldThrow<RuntimeException> {
            runBlocking {
                meldepliktConnector("false", 500).hentMeldestatus(1L)
            }
        }
    }
}
