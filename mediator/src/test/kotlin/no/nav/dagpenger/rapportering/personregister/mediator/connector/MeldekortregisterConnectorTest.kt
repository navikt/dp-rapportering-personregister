package no.nav.dagpenger.rapportering.personregister.mediator.connector

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.rapportering.personregister.mediator.Configuration.defaultObjectMapper
import no.nav.dagpenger.rapportering.personregister.mediator.utils.MetrikkerTestUtil.actionTimer
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class MeldekortregisterConnectorTest {
    init {
        System.setProperty("KAFKA_SCHEMA_REGISTRY", "KAFKA_SCHEMA_REGISTRY")
        System.setProperty("KAFKA_SCHEMA_REGISTRY_USER", "KAFKA_SCHEMA_REGISTRY_USER")
        System.setProperty("KAFKA_SCHEMA_REGISTRY_PASSWORD", "KAFKA_SCHEMA_REGISTRY_PASSWORD")
        System.setProperty("KAFKA_BROKERS", "KAFKA_BROKERS")
    }

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
    fun `returnerer seneste innsendtmeldekort`() {
        val now = LocalDateTime.now()
        val body =
            defaultObjectMapper.writeValueAsString(
                listOf(
                    InnsendtMeldekortResponse(now.minusDays(28), now.minusDays(15), now.minusDays(14)),
                    InnsendtMeldekortResponse(now.minusDays(14), now.minusDays(1), now.minusDays(1)),
                ),
            )

        val result = runBlocking { connector(body, 200).hentSisteInnsendteMeldekort() }

        result shouldNotBe null
        result!!.innsendtTidspunkt shouldBe now.minusDays(1)
    }

    @Test
    fun `returnerer null ved tom liste`() {
        val result = runBlocking { connector("[]", 200).hentSisteInnsendteMeldekort() }

        result shouldBe null
    }

    @Test
    fun `returnerer null ved 404`() {
        val result = runBlocking { connector("{}", 404).hentSisteInnsendteMeldekort() }

        result shouldBe null
    }

    @Test
    fun `kaster exception ved uventet statuskode`() {
        shouldThrow<RuntimeException> {
            runBlocking { connector("{}", 500).hentSisteInnsendteMeldekort() }
        }
    }
}
