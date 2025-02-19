package no.nav.dagpenger.rapportering.personregister.mediator.service

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.rapportering.personregister.mediator.connector.ArbeidssøkerConnector
import no.nav.dagpenger.rapportering.personregister.mediator.db.ArbeidssøkerRepository
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.utils.arbeidssøkerResponse
import no.nav.dagpenger.rapportering.personregister.mediator.utils.kafka.MockKafkaProducer
import no.nav.dagpenger.rapportering.personregister.modell.Arbeidssøkerperiode
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class ArbeidssøkerServiceTest {
    private val personRepository = mockk<PersonRepository>()
    private val arbeidssøkerRepository = mockk<ArbeidssøkerRepository>()
    val arbeidssøkerConnector = mockk<ArbeidssøkerConnector>(relaxed = true)
    private val arbeidssøkerService =
        ArbeidssøkerService(
            personRepository,
            arbeidssøkerRepository,
            arbeidssøkerConnector,
        )

    private val ident = "12345678901"

    @Test
    fun `kan hente siste arbeidssøkerperiode`() {
        val response = arbeidssøkerResponse(UUID.randomUUID())
        coEvery { arbeidssøkerConnector.hentSisteArbeidssøkerperiode(ident) } returns listOf(response)

        val periode = runBlocking { arbeidssøkerService.hentSisteArbeidssøkerperiode(ident) }

        response.periodeId shouldBe periode?.periodeId
        response.startet.tidspunkt shouldBe periode?.startet
    }

    @Test
    fun `kan oppdatere overtagelse i databasen`() {
        justRun { arbeidssøkerRepository.oppdaterOvertagelse(any(), any()) }

        val periodeId = UUID.randomUUID()

        arbeidssøkerService.oppdaterOvertagelse(periodeId, true)

        verify { arbeidssøkerRepository.oppdaterOvertagelse(periodeId, true) }
    }

    @Test
    fun `erArbeidssøker svarer riktig basert på periodens avsluttetDato`() {
        val arbeidssøkerperiode =
            Arbeidssøkerperiode(
                periodeId = UUID.randomUUID(),
                ident = "12345678901",
                startet = LocalDateTime.now(),
                avsluttet = null,
                overtattBekreftelse = false,
            )
        every { arbeidssøkerRepository.hentArbeidssøkerperioder(any()) } returns listOf(arbeidssøkerperiode) andThen
            listOf(arbeidssøkerperiode.copy(avsluttet = LocalDateTime.now().minusDays(1)))

        arbeidssøkerService.erArbeidssøker(ident) shouldBe true
        arbeidssøkerService.erArbeidssøker(ident) shouldBe false
    }
}
