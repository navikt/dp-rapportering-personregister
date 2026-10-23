package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.dagpenger.rapportering.personregister.mediator.service.ManuellMigreringAvMeldekortForBrukereSomBareHarAvslagService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

class ManuellMigreringAvMeldekortForBrukereSomBareHarAvslagMottakTest {
    private val testRapid = TestRapid()
    private val manuellMigreringAvMeldekortForBrukereSomBareHarAvslagService =
        mockk<ManuellMigreringAvMeldekortForBrukereSomBareHarAvslagService>(relaxed = true)

    init {
        ManuellMigreringAvMeldekortForBrukereSomBareHarAvslagMottak(testRapid, manuellMigreringAvMeldekortForBrukereSomBareHarAvslagService)
    }

    @BeforeEach
    fun setup() {
        testRapid.reset()
    }

    @Test
    fun `onPacket kaller migrerMeldekort med forventede personIder`() {
        val melding =
            """
            {
              "@event_name": "migrer_meldekort_for_brukere_som_bare_har_avslag",
              "personIder": ["712", "3314", "548"]
            }
            """.trimIndent()

        testRapid.sendTestMessage(melding)

        verify(exactly = 1) {
            manuellMigreringAvMeldekortForBrukereSomBareHarAvslagService.migrerMeldekort(listOf(712, 3314, 548))
        }
    }

    @Test
    fun `onPacket kaller migrerMeldekort med tom liste hvis meldingen ikke inneholder noen personIder`() {
        val melding =
            """
            {
              "@event_name": "migrer_meldekort_for_brukere_som_bare_har_avslag",
              "personIder": []
            }
            """.trimIndent()

        testRapid.sendTestMessage(melding)

        verify(exactly = 1) {
            manuellMigreringAvMeldekortForBrukereSomBareHarAvslagService.migrerMeldekort(listOf())
        }
    }

    @Test
    fun `onPacket kaster ikke exception selv om personIder ikke lar seg caste til Long`() {
        val melding =
            """
            {
              "@event_name": "migrer_meldekort_for_brukere_som_bare_har_avslag",
              "personIder": ["ikke en long"]
            }
            """.trimIndent()

        try {
            testRapid.sendTestMessage(melding)
        } catch (e: Exception) {
            fail { "Her skal det ikke kastes exception, men det ble kastet en $e" }
        }
    }

    @Test
    fun `onPacket kaller migrerMeldekort hvis key personIder er null`() {
        val melding =
            """
            {
              "@event_name": "migrer_meldekort_for_brukere_som_bare_har_avslag",
              "personIder": null
            }
            """.trimIndent()

        testRapid.sendTestMessage(melding)

        verify(exactly = 0) {
            manuellMigreringAvMeldekortForBrukereSomBareHarAvslagService.migrerMeldekort(any())
        }
    }

    @Test
    fun `onPacket kaller ikke migrerMeldekort hvis key personIder mangler i meldingen`() {
        val melding =
            """
            {
              "@event_name": "migrer_meldekort_for_brukere_som_bare_har_avslag"
            }
            """.trimIndent()

        testRapid.sendTestMessage(melding)

        verify(exactly = 0) {
            manuellMigreringAvMeldekortForBrukereSomBareHarAvslagService.migrerMeldekort(any())
        }
    }

    @Test
    fun `onPacket kaster ikke exception selv om kall til migrerMeldkort feiler`() {
        every { manuellMigreringAvMeldekortForBrukereSomBareHarAvslagService.migrerMeldekort(any()) } throws RuntimeException("kaboom")
        val melding =
            """
            {
              "@event_name": "migrer_meldekort_for_brukere_som_bare_har_avslag",
              "personIder": ["712", "3314", "548"]
            }
            """.trimIndent()

        try {
            testRapid.sendTestMessage(melding)
        } catch (e: Exception) {
            fail { "Her skal det ikke kastes exception, men det ble kastet en $e" }
        }
    }
}
