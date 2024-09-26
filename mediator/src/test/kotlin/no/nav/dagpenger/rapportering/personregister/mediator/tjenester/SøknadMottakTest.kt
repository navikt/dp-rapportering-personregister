package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.mockk.mockk
import io.mockk.verify
import no.nav.dagpenger.rapportering.personregister.mediator.PersonstatusMediator
import no.nav.dagpenger.rapportering.personregister.mediator.hendelser.SøknadHendelse
import no.nav.dagpenger.rapportering.personregister.mediator.utils.MetrikkerTestUtil.soknadMetrikker
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class SøknadMottakTest {
    private val testRapid = TestRapid()
    private val rapporteringMediator = mockk<PersonstatusMediator>(relaxed = true)

    init {
        SøknadMottak(testRapid, rapporteringMediator, soknadMetrikker)
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
        val dato = LocalDateTime.parse("2024-09-01T11:00:27.899791748")

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
      "søknadId": "123e4567-e89b-12d3-a456-426614174000",
      "ident": "12345678903",
      "søknadstidspunkt": "2024-09-01T11:00:27.899791748"
    }
    """.trimIndent()
