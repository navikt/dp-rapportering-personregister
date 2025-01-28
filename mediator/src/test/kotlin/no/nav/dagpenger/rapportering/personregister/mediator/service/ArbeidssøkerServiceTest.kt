package no.nav.dagpenger.rapportering.personregister.mediator.service

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import no.nav.dagpenger.rapportering.personregister.mediator.db.ArbeidssøkerRepository
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.modell.Arbeidssøkerperiode
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class ArbeidssøkerServiceTest {
    private val testRapid = TestRapid()
    private val personRepository = mockk<PersonRepository>()
    private val arbeidssøkerRepository = mockk<ArbeidssøkerRepository>()
    private val arbeidssøkerService = ArbeidssøkerService(testRapid, personRepository, arbeidssøkerRepository)

    private val ident = "12345678901"

    @Test
    fun `kan sende OvertaBekreftelseBehov`() {
        val periodeId = UUID.randomUUID()

        arbeidssøkerService.sendOvertaBekreftelseBehov(ident, periodeId)

        with(testRapid.inspektør) {
            size shouldBe 1
            message(0)["@event_name"].asText() shouldBe "behov_arbeidssokerstatus"
            message(0)["@behov"][0].asText() shouldBe "OvertaBekreftelse"
            message(0)["ident"].asText() shouldBe ident
            message(0)["periodeId"].asText() shouldBe periodeId.toString()
        }
    }

    @Test
    fun `kan sende ArbeidssøkerstatusBehov`() {
        arbeidssøkerService.sendArbeidssøkerBehov(ident)

        with(testRapid.inspektør) {
            size shouldBe 1
            message(0)["@event_name"].asText() shouldBe "behov_arbeidssokerstatus"
            message(0)["@behov"][0].asText() shouldBe "Arbeidssøkerstatus"
            message(0)["ident"].asText() shouldBe ident
        }
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
