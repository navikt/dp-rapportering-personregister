package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.dagpenger.rapportering.personregister.mediator.db.MeldingerRepository
import no.nav.dagpenger.rapportering.personregister.mediator.service.BehandlingService
import no.nav.dagpenger.rapportering.personregister.mediator.utils.MetrikkerTestUtil.vedtakMetrikker
import no.nav.dagpenger.rapportering.personregister.mediator.utils.UUIDv7
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tools.jackson.databind.ObjectMapper
import java.time.LocalDateTime

class VedtakFattetUtenforArenaMottakTest {
    private val testRapid = TestRapid()
    private val behandlingService = mockk<BehandlingService>(relaxed = true)
    private val meldingerRepository = mockk<MeldingerRepository>(relaxed = true)

    init {
        VedtakFattetUtenforArenaMottak(testRapid, behandlingService, meldingerRepository, vedtakMetrikker)
    }

    @BeforeEach
    fun setup() {
        testRapid.reset()
    }

    @Test
    fun `onPacket behandler melding og inkrementerer metrikk for innvilget vedtak`() {
        val metrikkCount = vedtakMetrikker.vedtakFattetUtenforArenaMottatt.count()
        val id = UUIDv7.newUuid()
        val behandlingId = UUIDv7.newUuid().toString()
        val søknadId = UUIDv7.newUuid().toString()
        val ident = "01020312345"
        val sakId = UUIDv7.newUuid().toString()

        testRapid.sendTestMessage(lagMelding(id.toString(), behandlingId, søknadId, ident, sakId))

        verify(exactly = 1) {
            behandlingService.behandle(
                match { hendelse ->
                    hendelse.korrelasjonsId == id &&
                        hendelse.ident == ident &&
                        hendelse.dato.isBefore(LocalDateTime.now().plusSeconds(1)) &&
                        hendelse.referanseId == id.toString() &&
                        hendelse.behandlingId == behandlingId &&
                        hendelse.søknadId == søknadId &&
                        hendelse.sakId == sakId
                },
            )
        }
        verify(exactly = 1) {
            meldingerRepository.lagreInnkommendeMelding(
                id,
                ident,
                match { melding ->
                    with(ObjectMapper().readTree(melding)) {
                        this["@event_name"].asString() == "vedtak_fattet_utenfor_arena" &&
                            this["behandlingId"].asString() == behandlingId &&
                            this["søknadId"].asString() == søknadId &&
                            this["sakId"].asString() == sakId
                    }
                },
            )
        }
        vedtakMetrikker.vedtakFattetUtenforArenaMottatt.count() shouldBe metrikkCount + 1
    }

    @Test
    fun `onPacket kaster exception og inkrementerer feilmetrikk hvis behandling av melding feiler`() {
        val metrikkCount = vedtakMetrikker.vedtakFattetUtenforArenaFeilet.count()
        every { behandlingService.behandle(any()) } throws RuntimeException("kaboom")

        val exception =
            shouldThrow<RuntimeException> {
                testRapid.sendTestMessage(lagMelding())
            }

        exception.message shouldBe "kaboom"
        vedtakMetrikker.vedtakFattetUtenforArenaFeilet.count() shouldBe metrikkCount + 1
    }

    @Test
    fun `onPacket behandler ikke avslag`() {
        testRapid.sendTestMessage(lagMelding(førteTil = "Avslag"))

        verify(exactly = 0) {
            meldingerRepository.lagreInnkommendeMelding(any(), any(), any())
            behandlingService.behandle(any())
        }
    }

    private fun lagMelding(
        id: String = UUIDv7.newUuid().toString(),
        behandlingId: String = UUIDv7.newUuid().toString(),
        søknadId: String = UUIDv7.newUuid().toString(),
        ident: String = "01020312345",
        sakId: String = UUIDv7.newUuid().toString(),
        førteTil: String = "Innvilgelse",
    ) = //language=json
        """
        {
          "@id": "$id",
          "@event_name": "vedtak_fattet_utenfor_arena",
          "behandlingId": "$behandlingId",
          "søknadId": "$søknadId",
          "ident": "$ident",
          "sakId": "$sakId",
          "førteTil": "$førteTil"
        }
        """.trimIndent()
}
