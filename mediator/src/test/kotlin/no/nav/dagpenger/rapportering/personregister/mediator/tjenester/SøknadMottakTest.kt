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
        val dato = "2024-10-03T16:14:40+02:00"

        testRapid.sendTestMessage(lagSøknadInnsendtEvent(ident, dato, søknadId))

        val søknadHendelse =
            SøknadHendelse(
                ident,
                dato.toLocalDateTime(),
                søknadId,
            )

        verify(exactly = 1) { personstatusMediator.behandle(søknadHendelse) }
    }
}

private fun lagSøknadInnsendtEvent(
    ident: String,
    dato: String,
    søknadId: String,
): String {
    //language=json
    return """
        {
          "@event_name": "søknad_innsendt_varsel",
          "ident": "$ident",
          "søknadId": "$søknadId",
          "søknadstidspunkt": "$dato" 
          
        }
        """.trimIndent()
}

private fun String.toLocalDateTime() = OffsetDateTime.parse(this).toLocalDateTime()
