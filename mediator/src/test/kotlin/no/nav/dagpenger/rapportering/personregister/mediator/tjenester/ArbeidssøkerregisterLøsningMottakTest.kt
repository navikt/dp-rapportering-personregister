package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.mockk.mockk
import io.mockk.verify
import no.nav.dagpenger.rapportering.personregister.mediator.ArbeidssøkerMediator
import no.nav.dagpenger.rapportering.personregister.modell.Arbeidssøkerperiode
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ArbeidssøkerregisterLøsningMottakTest {
    private val testRapid = TestRapid()
    private val arbeidssøkerMediator = mockk<ArbeidssøkerMediator>(relaxed = true)

    init {
        ArbeidssøkerregisterLøsningMottak(testRapid, arbeidssøkerMediator)
    }

    @BeforeEach
    fun setup() {
        testRapid.reset()
    }

    @Test
    fun `skal motta arbeidssøkerregister løsning event`() {
        testRapid.sendTestMessage(løsning_arbeidssøkerstatus_behov)

        verify(exactly = 1) { arbeidssøkerMediator.behandle(any<Arbeidssøkerperiode>()) }
    }
}

private val løsning_arbeidssøkerstatus_behov =
    // language=json
    """
    {
      "@event_name": "behov_arbeissokerstatus",
      "@behov": ["Arbeidssøkerstatus"],
      "ident": "12345678910",
      "@id": "98be414a-e7f6-4b70-8fc7-c147e34dff9c",
      "@opprettet": "2025-01-10T08:04:33.71863",
      "system_read_count": 0,
      "system_participating_services": [
        {
          "id": "98be414a-e7f6-4b70-8fc7-c147e34dff9c",
          "time": "2025-01-10T08:04:33.718630"
        }
      ],
      "@løsning": {
        "Arbeidssøkerstatus": {
          "verdi": {
            "ident": "12345678910",
            "periodeId": "0f747c19-df77-4879-9569-3f7b0f188a6f",
            "startDato": "2024-12-20T08:04:33.692289",
            "sluttDato": null
          }
        }
      },
      "@feil": {
        "Arbeidssøkerstatus": {
          "verdi": null
        }
      }
    }
    """.trimIndent()
