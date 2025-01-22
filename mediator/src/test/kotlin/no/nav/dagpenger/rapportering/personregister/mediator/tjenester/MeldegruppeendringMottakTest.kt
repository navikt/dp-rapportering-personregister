package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.mockk.mockk
import io.mockk.verify
import no.nav.dagpenger.rapportering.personregister.mediator.PersonstatusMediator
import no.nav.dagpenger.rapportering.personregister.modell.StansHendelse
import org.junit.jupiter.api.Test

class MeldegruppeendringMottakTest {
    private val testRapid = TestRapid()
    private val personstatusMediator = mockk<PersonstatusMediator>(relaxed = true)
    private val meldegruppeendringMottak = MeldegruppeendringMottak(testRapid, personstatusMediator)

    @Test
    fun `kan motta meldegruppendring event`() {
        testRapid.sendTestMessage(meldegruppeendring_event)

        verify(exactly = 1) { personstatusMediator.behandle(any<StansHendelse>()) }
    }

    @Test
    fun `kan motta meldegruppendring event uten 'tilOgMed' dato`() {
        testRapid.sendTestMessage(meldegruppeendring_uten_tom_dato)

        verify(exactly = 1) { personstatusMediator.behandle(any<StansHendelse>()) }
    }
}

private val meldegruppeendring_event =
    //language=json
    """
    {
      "table": "ARENA_GOLDENGATE.MELDEGRUPPE",
      "FODSELSNR": "12345678903",
      "MELDEGRUPPEKODE": "DAGP",
      "DATO_FRA": "2024-10-03",
      "DATO_TIL": "2025-10-03",
      "HENDELSE_ID": 12345678 
    }
    """.trimIndent()

private val meldegruppeendring_uten_tom_dato =
    //language=json
    """
    {
      "table": "ARENA_GOLDENGATE.MELDEGRUPPE",
      "FODSELSNR": "12345678903",
      "MELDEGRUPPEKODE": "DAGP",
      "DATO_FRA": "2024-10-03",
      "HENDELSE_ID": 12345678 
    }
    """.trimIndent()
