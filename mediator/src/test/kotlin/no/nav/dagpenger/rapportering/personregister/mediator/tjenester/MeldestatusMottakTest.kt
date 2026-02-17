package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.dagpenger.rapportering.personregister.mediator.MeldestatusMediator
import no.nav.dagpenger.rapportering.personregister.mediator.utils.MetrikkerTestUtil.meldestatusMetrikker
import no.nav.dagpenger.rapportering.personregister.modell.meldestatus.MeldestatusHendelse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MeldestatusMottakTest {
    private val testRapid = TestRapid()
    private val meldestatusMediator = mockk<MeldestatusMediator>(relaxed = true)

    init {
        MeldestatusMottak(testRapid, meldestatusMediator, meldestatusMetrikker)
    }

    @BeforeEach
    fun setUp() {
        testRapid.reset()
    }

    @Test
    fun `onPacket behandler melding og inkrementerer mottakmetrikk`() {
        val metrikkCount = meldestatusMetrikker.meldestatusMottatt.count()

        testRapid.sendTestMessage(lagMeldestatusEndringEvent())

        val forventetHendelse =
            MeldestatusHendelse(
                personId = 5268057,
                meldestatusId = 95,
                hendelseId = 95,
            )

        meldestatusMetrikker.meldestatusMottatt.count() shouldBe metrikkCount + 1
        verify(exactly = 1) { meldestatusMediator.behandle(forventetHendelse) }
    }

    @Test
    fun `onPacket kaster exception og inkrementerer feilmetrikk hvis behandling av meldestatus fra Arena feiler`() {
        val metrikkCount = meldestatusMetrikker.meldestatusFeilet.count()
        every { meldestatusMediator.behandle(any()) } throws RuntimeException("kaboom")

        val exception =
            shouldThrow<RuntimeException> {
                testRapid.sendTestMessage(lagMeldestatusEndringEvent())
            }

        exception.message shouldBe "kaboom"
        meldestatusMetrikker.meldestatusFeilet.count() shouldBe metrikkCount + 1
    }
}

private fun lagMeldestatusEndringEvent() =
    //language=json
    """
  {
    "table": "ARENA_GOLDENGATE.MELDESTATUS",
    "op_type": "I",
    "op_ts": "2025-09-02 15:41:29.000000",
    "current_ts": "2025-09-02 15:41:34.942000",
    "pos": "00000000050001000181",
    "after": {
        "MELDESTATUS_ID": 95,
        "PERSON_ID": 5268057,
        "TRANS_ID": "5837.9.6853",
        "HENDELSE_ID": 95,
        "OPPRETTET_DATO": "2025-09-02 15:41:29",
        "OPPRETTET_AV": "DIGIDAG",
        "ENDRET_DATO": "2025-09-02 15:41:29",
        "ENDRET_AV": "DIGIDAG"
    }
}
    
    """.trimIndent()
