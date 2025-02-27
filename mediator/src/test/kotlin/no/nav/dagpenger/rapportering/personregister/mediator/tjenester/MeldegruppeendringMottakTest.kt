package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.mockk.mockk
import io.mockk.verify
import no.nav.dagpenger.rapportering.personregister.mediator.FremtidigHendelseMediator
import no.nav.dagpenger.rapportering.personregister.mediator.PersonstatusMediator
import no.nav.dagpenger.rapportering.personregister.modell.AnnenMeldegruppeHendelse
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class MeldegruppeendringMottakTest {
    private val testRapid = TestRapid()
    private val personstatusMediator = mockk<PersonstatusMediator>(relaxed = true)
    private val fremtidigHendelseMediator = mockk<FremtidigHendelseMediator>(relaxed = true)

    init {
        MeldegruppeendringMottak(testRapid, personstatusMediator, fremtidigHendelseMediator)
    }

    @Test
    fun `kan motta meldegruppendring event`() {
        testRapid.sendTestMessage(meldegruppeendring_event)

        verify(exactly = 1) { personstatusMediator.behandle(any<AnnenMeldegruppeHendelse>()) }
    }

    @Test
    fun `kan motta meldegruppendring event uten 'tilOgMed' dato`() {
        testRapid.sendTestMessage(meldegruppeendring_uten_tom_dato)

        verify(exactly = 1) { personstatusMediator.behandle(any<AnnenMeldegruppeHendelse>()) }
    }

    @Test
    fun `kan motta fremtidig meldegruppendring event`() {
        testRapid.sendTestMessage(fremtidig_meldegruppeendring_event)

        println(fremtidig_meldegruppeendring_event)

        verify(exactly = 1) { fremtidigHendelseMediator.behandle(any()) }
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
           "DATO_TIL": "2024-10-03 00:00:00",
           "HENDELSESDATO": "2021-06-08 14:05:10",
           "FODSELSNR": "##MASKERT##",
           "HENDELSE_ID": 3773236
       }
    }
    """.trimIndent()

private val dagensDatoFormatert = LocalDateTime.now().plusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
private val fremtidig_meldegruppeendring_event =
    //language=json
    """
    {
       "table": "ARENA_GOLDENGATE.MELDEGRUPPE",
       "after": {
           "MELDEGRUPPE_ID": 51685079,
           "MELDEGRUPPEKODE": "ARBS",
           "DATO_FRA": "$dagensDatoFormatert",
           "DATO_TIL": null,
           "HENDELSESDATO": "2021-06-08 14:05:10",
           "FODSELSNR": "##MASKERT##",
           "HENDELSE_ID": 3773236
       }
    }
    """.trimIndent()
