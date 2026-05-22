package no.nav.dagpenger.rapportering.personregister.mediator.connector

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.rapportering.personregister.mediator.Configuration.defaultObjectMapper
import no.nav.dagpenger.rapportering.personregister.mediator.utils.MetrikkerTestUtil.actionTimer
import org.junit.jupiter.api.Test
import java.time.LocalDate

class MeldekortregisterConnectorTest {
    init {
        System.setProperty("KAFKA_SCHEMA_REGISTRY", "KAFKA_SCHEMA_REGISTRY")
        System.setProperty("KAFKA_SCHEMA_REGISTRY_USER", "KAFKA_SCHEMA_REGISTRY_USER")
        System.setProperty("KAFKA_SCHEMA_REGISTRY_PASSWORD", "KAFKA_SCHEMA_REGISTRY_PASSWORD")
        System.setProperty("KAFKA_BROKERS", "KAFKA_BROKERS")
    }

    private val ident = "12345678901"

    private fun connector(
        responseBody: String,
        statusCode: Int,
    ) = MeldekortregisterConnector(
        meldekortregisterUrl = "http://meldekortregister",
        meldekortregisterTokenProvider = { "testToken" },
        httpClient = createMockClient(statusCode, responseBody),
        actionTimer = actionTimer,
    )

    @Test
    fun `returnerer fastsattMeldedato`() {
        val forventet = LocalDate.now().minusDays(1)
        val body = defaultObjectMapper.writeValueAsString(SisteFastsattMeldedatoResponse(forventet))

        val result = runBlocking { connector(body, 200).hentSisteFastsattMeldedato(ident) }

        result shouldBe forventet
    }

    @Test
    fun `returnerer null når bruker ikke har fastsattMeldedato`() {
        val body = defaultObjectMapper.writeValueAsString(SisteFastsattMeldedatoResponse(null))

        val result = runBlocking { connector(body, 200).hentSisteFastsattMeldedato(ident) }

        result shouldBe null
    }

    @Test
    fun `returnerer null ved 404`() {
        val result = runBlocking { connector("{}", 404).hentSisteFastsattMeldedato(ident) }

        result shouldBe null
    }

    @Test
    fun `kaster exception ved uventet statuskode`() {
        shouldThrow<RuntimeException> {
            runBlocking { connector("{}", 500).hentSisteFastsattMeldedato(ident) }
        }
    }
}
