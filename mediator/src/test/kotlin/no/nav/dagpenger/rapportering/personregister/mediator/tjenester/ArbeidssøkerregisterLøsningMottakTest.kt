package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.mockk.mockk
import io.mockk.verify
import no.nav.dagpenger.rapportering.personregister.mediator.ArbeidssøkerMediator
import no.nav.dagpenger.rapportering.personregister.modell.ArbeidssøkerperiodeLøsning
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

        verify(exactly = 1) { arbeidssøkerMediator.behandle(any<ArbeidssøkerperiodeLøsning>()) }
    }
}

private val løsning_arbeidssøkerstatus_behov =
    // language=json
    """
    {
  "@event_name": "behov_arbeidssokerstatus",
  "@behov": ["Arbeidssøkerstatus"],
  "ident": "12917899809",
  "@id": "24082462-460b-43e4-81d8-c95331c76335",
  "@opprettet": "2025-01-24T09:33:14.979019295",
  "system_read_count": 1,
  "system_participating_services": [
    {
      "id": "24082462-460b-43e4-81d8-c95331c76335",
      "time": "2025-01-24T09:33:14.979019295",
      "service": "dp-rapportering-personregister",
      "instance": "dp-rapportering-personregister-85d6674d66-9lq67",
      "image": "europe-north1-docker.pkg.dev/nais-management-233d/teamdagpenger/dp-rapportering-personregister:2025.01.24-08.29-434c52e"
    },
    {
      "id": "24082462-460b-43e4-81d8-c95331c76335",
      "time": "2025-01-24T09:33:14.984067030",
      "service": "dp-arbeidssokerregister-adapter",
      "instance": "dp-arbeidssokerregister-adapter-5dbc7f6647-gb295",
      "image": "europe-north1-docker.pkg.dev/nais-management-233d/teamdagpenger/dp-arbeidssokerregister-adapter:2025.01.22-13.36-b15f180"
    }
  ],
  "@løsning": {
    "Arbeidssøkerstatus": {
      "verdi": {
        "ident": "12917899809",
        "periodeId": "e660a201-3162-480f-bbb0-21c85e42fa03",
        "startDato": "2025-01-21T13:09:52.237",
        "sluttDato": "2025-01-22T13:15:06.125"
      }
    }
  },
  "@feil": { "Arbeidssøkerstatus": { "verdi": null } }
}
    """.trimIndent()
