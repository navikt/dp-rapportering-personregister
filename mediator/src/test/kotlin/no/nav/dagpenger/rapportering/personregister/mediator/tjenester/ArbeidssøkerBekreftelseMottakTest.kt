package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.mockk.coVerify
import io.mockk.mockk
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
    fun `mottar og sender riktig melding til service`() {
        testRapid.sendTestMessage(ArbeidssøkerBekreftelseTestData.event())

        coVerify(exactly = 1) {
            arbeidssøkerBekreftelseService.behandle(
                match { melding ->
                    melding.ident == ArbeidssøkerBekreftelseTestData.ident &&
                        melding.bekreftelse.periodeId == ArbeidssøkerBekreftelseTestData.periodeId &&
                        melding.bekreftelse.id == ArbeidssøkerBekreftelseTestData.bekreftelseId &&
                        melding.bekreftelse.svar.harJobbetIDennePerioden &&
                        melding.bekreftelse.svar.vilFortsetteSomArbeidssøker
                },
            )
        }
    }

    @Test
    fun `tar ikke imot meldinger som mangler påkrevde felter`() {
        testRapid.sendTestMessage(
            """
            {
              "@event_name": "arbeidssøkerbekreftelse",
              "bekreftelse": {}
            }
            """.trimIndent(),
        )

        coVerify(exactly = 0) { arbeidssøkerBekreftelseService.behandle(any()) }
    }
}
