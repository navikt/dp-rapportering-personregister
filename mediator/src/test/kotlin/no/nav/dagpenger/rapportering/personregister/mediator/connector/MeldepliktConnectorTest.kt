package no.nav.dagpenger.rapportering.personregister.mediator.connector

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.rapportering.personregister.mediator.utils.MetrikkerTestUtil.actionTimer
import org.junit.jupiter.api.Test

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
    fun `Kan mappe respons fra meldepliktAdapter`() {
        val response = runBlocking { meldepliktConnector("true", 200).hentMeldeplikt("12345678901") }

        response shouldBe true
    }

    @Test
    fun `Feilstatus fra meldepliktAdapter trigger RuntimeException`() {
        shouldThrow<RuntimeException> {
            runBlocking {
                meldepliktConnector("false", 500).hentMeldeplikt("12345678901")
            }
        }
    }
}
