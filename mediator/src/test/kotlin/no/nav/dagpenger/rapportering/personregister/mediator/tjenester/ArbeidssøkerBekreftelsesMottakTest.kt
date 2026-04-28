package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.mockk.mockk
import io.mockk.verify
import no.nav.dagpenger.rapportering.personregister.mediator.service.ArbeidssøkerBekreftelseService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

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
        val ident = "12345678903"

        testRapid.sendTestMessage(lagArbeidssøkerBekreftelseEvent(ident))

        verify(exactly = 1) { arbeidssøkerBekreftelseService.behandle(any()) }
    }
}

private fun lagArbeidssøkerBekreftelseEvent(ident: String): String =
    //language=json
    """
    {
      "@event_name": "arbeidssøkerbekreftelse",
      "ident": "$ident",
      "bekreftelse": {
        "periodeId": "${UUID.randomUUID()}",
        "bekreftelsesløsning": "DAGPENGER",
        "id": "${UUID.randomUUID()}",
        "svar": {
          "sendtInnAv": {
            "tidspunkt": "${LocalDateTime.now()}",
            "utførtAv": {
              "type": "Person",
              "ident": "$ident",
              "sikkerhetsnivå": "1"
            },
            "kilde": "DAGPENGER",
            "årsak": "VET IKKE"
          },
          "gjelderFra":  "${LocalDateTime.now()}",
          "gjelderTil":  "${LocalDateTime.now()}",
          "harJobbetIDennePerioden": true,
          "vilFortsetteSomArbeidssøker": true
        }
      }
       }
    """.trimIndent()
