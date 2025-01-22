package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.mockk.mockk
import io.mockk.verify
import no.nav.dagpenger.rapportering.personregister.mediator.PersonstatusMediator
import no.nav.dagpenger.rapportering.personregister.mediator.utils.MetrikkerTestUtil.soknadMetrikker
import no.nav.dagpenger.rapportering.personregister.modell.SøknadHendelse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime

class SøknadMottakTest {
    private val testRapid = TestRapid()
    private val personstatusMediator = mockk<PersonstatusMediator>(relaxed = true)

    init {
        SøknadMottak(testRapid, personstatusMediator, soknadMetrikker)
    }

    @BeforeEach
    fun setup() {
        testRapid.reset()
    }

    @Test
    fun `skal motta søknad innsendt event`() {
        val ident = "12345678903"
        val søknadId = "123e4567-e89b-12d3-a456-426614174000"
        val dato = OffsetDateTime.parse("2024-10-03T16:14:40+02:00").toLocalDateTime()

        testRapid.sendTestMessage(lagSøknadInnsendtEvent())

        val søknadHendelse =
            SøknadHendelse(
                ident,
                dato,
                søknadId,
            )

        verify(exactly = 1) { personstatusMediator.behandle(søknadHendelse) }
    }
}

private fun lagSøknadInnsendtEvent(): String {
    //language=json
    return """
        {
          "@event_name": "søknad_innsendt_varsel",
          "søknadId": "123e4567-e89b-12d3-a456-426614174000",
          "ident": "12345678903",
          "søknadstidspunkt": "2024-10-03T16:14:40+02:00" 
          
        }
        """.trimIndent()
}
