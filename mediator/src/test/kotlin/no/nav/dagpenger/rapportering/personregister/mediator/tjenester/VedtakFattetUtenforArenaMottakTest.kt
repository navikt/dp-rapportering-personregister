package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.dagpenger.rapportering.personregister.mediator.db.BehandlingRepository
import no.nav.dagpenger.rapportering.personregister.mediator.utils.MetrikkerTestUtil.vedtakMetrikker
import no.nav.dagpenger.rapportering.personregister.mediator.utils.UUIDv7
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class VedtakFattetUtenforArenaMottakTest {
    private val testRapid = TestRapid()
    private val behandlingRepository = mockk<BehandlingRepository>(relaxed = true)

    init {
        VedtakFattetUtenforArenaMottak(testRapid, behandlingRepository, vedtakMetrikker)
    }

    @BeforeEach
    fun setup() {
        testRapid.reset()
    }

    @Test
    fun `onPacket behandler melding og inkrementere metrikk`() {
        val metrikkCount = vedtakMetrikker.vedtakFattetUtenforArenaMottatt.count()
        val behandlingId = UUIDv7.newUuid().toString()
        val søknadId = UUIDv7.newUuid().toString()
        val ident = "01020312345"
        val sakId = UUIDv7.newUuid().toString()

        testRapid.sendTestMessage(lagMelding(behandlingId, søknadId, ident, sakId))

        verify(exactly = 1) {
            behandlingRepository.lagreData(
                eq(behandlingId),
                eq(søknadId),
                eq(ident),
                eq(sakId),
            )
        }
        vedtakMetrikker.vedtakFattetUtenforArenaMottatt.count() shouldBe metrikkCount + 1
    }

    @Test
    fun `onPacket kaster exception og inkrementerer feilmetrikk hvis behandling av melding feiler`() {
        val metrikkCount = vedtakMetrikker.vedtakFattetUtenforArenaFeilet.count()
        every { behandlingRepository.lagreData(any(), any(), any(), any()) } throws RuntimeException("kaboom")

        val exception =
            shouldThrow<RuntimeException> {
                testRapid.sendTestMessage(lagMelding())
            }

        exception.message shouldBe "kaboom"
        vedtakMetrikker.vedtakFattetUtenforArenaFeilet.count() shouldBe metrikkCount + 1
    }

    private fun lagMelding(
        behandlingId: String = UUIDv7.newUuid().toString(),
        søknadId: String = UUIDv7.newUuid().toString(),
        ident: String = "01020312345",
        sakId: String = UUIDv7.newUuid().toString(),
    ) = //language=json
        """
        {
          "@event_name": "vedtak_fattet_utenfor_arena",
          "behandlingId": "$behandlingId",
          "søknadId": "$søknadId",
          "ident": "$ident",
          "sakId": "$sakId"
        }
        """.trimIndent()
}
