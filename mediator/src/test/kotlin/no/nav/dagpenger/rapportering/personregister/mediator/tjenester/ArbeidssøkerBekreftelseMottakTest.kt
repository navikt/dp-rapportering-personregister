package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.rapportering.personregister.mediator.service.ArbeidssøkerBekreftelseService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ArbeidssøkerBekreftelseMottakTest {
    private val testRapid = TestRapid()
    private val arbeidssøkerBekreftelseService = mockk<ArbeidssøkerBekreftelseService>(relaxed = true)

    init {
        ArbeidssøkerBekreftelseMottak(testRapid, arbeidssøkerBekreftelseService)
    }

    @BeforeEach
    fun setup() {
        testRapid.reset()
    }

    @Test
    fun `kan behandle arbeidssøkerbekreftelse hendelse `() {
        testRapid.sendTestMessage(ArbeidssøkerBekreftelseTestData.event())

        verify(exactly = 1) { runBlocking { arbeidssøkerBekreftelseService.behandle(any()) } }
    }
}
