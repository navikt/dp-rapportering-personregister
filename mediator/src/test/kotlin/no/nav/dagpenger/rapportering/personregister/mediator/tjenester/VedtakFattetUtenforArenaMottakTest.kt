package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.mockk.mockk
import io.mockk.verify
import no.nav.dagpenger.rapportering.personregister.mediator.db.BehandlingRepository
import no.nav.dagpenger.rapportering.personregister.mediator.utils.UUIDv7
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class VedtakFattetUtenforArenaMottakTest {
    private val testRapid = TestRapid()
    private val behandlingRepository = mockk<BehandlingRepository>(relaxed = true)

    init {
        VedtakFattetUtenforArenaMottak(testRapid, behandlingRepository)
    }

    @BeforeEach
    fun setup() {
        testRapid.reset()
    }

    @Test
    fun `skal OpprettMeldekortJob`() {
        val behandlingId = UUIDv7.newUuid().toString()
        val søknadId = UUIDv7.newUuid().toString()
        val ident = "01020312345"
        val sakId = UUIDv7.newUuid().toString()

        testRapid.sendTestMessage(
            """
            {
              "@event_name": "vedtak_fattet_utenfor_arena",
              "behandlingId": "$behandlingId",
              "søknadId": "$søknadId",
              "ident": "$ident",
              "sakId": "$sakId"
            }
            """.trimIndent(),
        )

        verify(exactly = 1) {
            behandlingRepository.lagreData(
                eq(behandlingId),
                eq(søknadId),
                eq(ident),
                eq(sakId),
            )
        }
    }
}
