package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.mockk.mockk
import io.mockk.verify
import no.nav.dagpenger.rapportering.personregister.mediator.PersonstatusMediator
import no.nav.dagpenger.rapportering.personregister.modell.RegistrertSomArbeidssøkerLøsning
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ArbeidssøkerMottakTest {
    private val rapid = TestRapid()
    private val personstatusMediator = mockk<PersonstatusMediator>(relaxed = true)

    init {
        ArbeidssøkerMottak(rapid, personstatusMediator)
    }

    @BeforeEach
    fun setup() {
        rapid.reset()
    }

    @Test
    fun `skal motta melding om bruker er arbeidssøker`() {
        rapid.sendTestMessage(json)

        verify(exactly = 1) { personstatusMediator.behandle(any<RegistrertSomArbeidssøkerLøsning>()) }
    }

    private val json =
        //language=JSON
        """
        {
          "@event_name": "behov",
          "@behov": ["RegistrertSomArbeidssøker"],
          "ident": "12345678910",
          "søknadId": "321",
          "behandlingId": "123",
          "gjelderDato": "2024-10-21",
          "@behovId": "5475a5e6-dece-495a-8171-f0ebd1cde177",
          "RegistrertSomArbeidssøker": { "Virkningsdato": "2024-10-21" },
          "@id": "1941ad9a-3cec-4c39-9d23-3264e2b976d4",
          "@opprettet": "2024-10-21T13:06:16.027430",
          "system_read_count": 1,
          "system_participating_services": [
            {
              "id": "4cf4e8a8-5772-47a4-a2da-0dbe046e5964",
              "time": "2024-10-21T13:06:15.985913"
            },
            {
              "id": "4cf4e8a8-5772-47a4-a2da-0dbe046e5964",
              "time": "2024-10-21T13:06:15.989382"
            },
            {
              "id": "1941ad9a-3cec-4c39-9d23-3264e2b976d4",
              "time": "2024-10-21T13:06:16.027430"
            }
          ],
          "@løsning": {
            "RegistrertSomArbeidssøker": {
              "verdi": true,
              "gyldigFraOgMed": "2024-10-21",
              "gyldigTilOgMed": "2024-10-21"
            }
          },
          "@kilde": {
            "navn": "paw-arbeidssoekerregisteret-api-oppslag",
            "data": [
              { "fom": "2020-01-01", "tom": "2019-12-25" },
              { "fom": "2019-12-24", "tom": "+999999999-12-31" }
            ]
          },
          "@forårsaket_av": {
            "id": "4cf4e8a8-5772-47a4-a2da-0dbe046e5964",
            "opprettet": "2024-10-21T13:06:15.985913",
            "event_name": "behov",
            "behov": ["RegistrertSomArbeidssøker"]
          }
        }
        """.trimIndent()
}
