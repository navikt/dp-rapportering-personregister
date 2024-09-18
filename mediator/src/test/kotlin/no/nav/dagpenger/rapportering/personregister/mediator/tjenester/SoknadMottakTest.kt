package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import io.mockk.mockk
import io.mockk.verify
import no.nav.dagpenger.rapportering.personregister.mediator.PersonstatusMediator
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class SoknadMottakTest {
    private val testRapid = TestRapid()
    private val rapporteringMediator = mockk<PersonstatusMediator>(relaxed = true)

    init {
        SoknadMottak(testRapid, rapporteringMediator)
    }

    @BeforeEach
    fun setup() {
        testRapid.reset()
    }

    @Test
    fun `skal motta søknad innsendt event`() {
        testRapid.sendTestMessage(søknad_innsendt_event)

        val ident = "12345678903"
        val søknadId = "123e4567-e89b-12d3-a456-426614174000"
        val dato = LocalDateTime.parse("2024-02-21T11:00:27.899791748")

        val søknadHendelse = SøknadHendelse(ident, søknadId, dato)

        verify(exactly = 1) { rapporteringMediator.behandle(søknadHendelse) }
    }
}

private val søknad_innsendt_event =
    //language=json
    """
    {
      "@id": "675eb2c2-bfba-4939-926c-cf5aac73d163",
      "@event_name": "søknad_innsendt_varsel",
      "@opprettet": "2024-02-21T11:00:27.899791748",
      "søknadId": "123e4567-e89b-12d3-a456-426614174000",
      "ident": "12345678903",
      "søknadstidspunkt": "2024-09-01T11:00:27.899791748",
      "søknadData": {
        "søknad_uuid": "123e4567-e89b-12d3-a456-426614174000",
        "@opprettet": "2024-02-21T11:00:27.899791748",
        "seksjoner": [
          {
            "fakta": [
              {
                "id": "6001",
                "svar": "NOR",
                "type": "land",
                "beskrivendeId": "faktum.hvilket-land-bor-du-i"
              }
            ],
            "beskrivendeId": "bostedsland"
          }
        ]
      }
    }
    """.trimIndent()
