package no.nav.dagpenger.rapportering.personregister.mediator.service

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.rapportering.personregister.mediator.ZONE_ID
import no.nav.dagpenger.rapportering.personregister.mediator.connector.ArbeidssøkerConnector
import no.nav.dagpenger.rapportering.personregister.mediator.utils.arbeidssøkerResponse
import org.junit.jupiter.api.Test
import java.time.ZoneId
import java.util.UUID

class ArbeidssøkerServiceTest {
    val arbeidssøkerConnector = mockk<ArbeidssøkerConnector>(relaxed = true)
    private val arbeidssøkerService = ArbeidssøkerService(arbeidssøkerConnector)

    private val ident = "12345678901"

    @Test
    fun `kan hente siste arbeidssøkerperiode`() {
        val response = arbeidssøkerResponse(UUID.randomUUID())
        coEvery { arbeidssøkerConnector.hentSisteArbeidssøkerperiode(ident) } returns listOf(response)

        val periode = runBlocking { arbeidssøkerService.hentSisteArbeidssøkerperiode(ident) }

        val startetUTC =
            periode
                ?.startet
                ?.atZone(ZONE_ID)
                ?.withZoneSameInstant(ZoneId.of("UTC"))
                ?.toOffsetDateTime()

        response.periodeId shouldBe periode?.periodeId
        response.startet.tidspunkt shouldBe startetUTC
    }
}
