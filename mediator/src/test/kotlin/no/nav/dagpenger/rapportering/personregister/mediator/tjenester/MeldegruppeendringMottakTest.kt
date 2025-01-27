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

    init {
        MeldegruppeendringMottak(testRapid, personstatusMediator)
    }

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
       "after": {
           "MELDEGRUPPE_ID": 51685079,
           "MELDEGRUPPEKODE": "ARBS",
           "DATO_FRA": "2021-05-17 00:00:00",
           "DATO_TIL": null,
           "HENDELSESDATO": "2021-06-08 14:05:10",
           "FODSELSNR": "##MASKERT##",
           "HENDELSE_ID": 3773236
       }
       }
    """.trimIndent()

private val meldegruppeendring_uten_tom_dato =
    //language=json
    """
    {
     "table": "ARENA_GOLDENGATE.MELDEGRUPPE",
       "after": {
           "MELDEGRUPPE_ID": 51685079,
           "MELDEGRUPPEKODE": "ARBS",
           "DATO_FRA": "2021-05-17 00:00:00",
           "DATO_TIL": "2024-10-03",
           "HENDELSESDATO": "2021-06-08 14:05:10",
           "FODSELSNR": "##MASKERT##",
           "HENDELSE_ID": 3773236
       }
    }
    """.trimIndent()
