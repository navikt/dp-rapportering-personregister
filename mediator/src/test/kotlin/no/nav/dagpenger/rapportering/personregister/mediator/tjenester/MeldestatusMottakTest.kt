package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.dagpenger.rapportering.personregister.mediator.Configuration.defaultObjectMapper
import no.nav.dagpenger.rapportering.personregister.mediator.MeldestatusMediator
import no.nav.dagpenger.rapportering.personregister.mediator.db.MeldingerRepository
import no.nav.dagpenger.rapportering.personregister.mediator.utils.MetrikkerTestUtil.meldestatusMetrikker
import no.nav.dagpenger.rapportering.personregister.modell.meldestatus.MeldestatusHendelse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MeldestatusMottakTest {
    private val testRapid = TestRapid()
    private val meldestatusMediator = mockk<MeldestatusMediator>(relaxed = true)
    private val meldingerRepository = mockk<MeldingerRepository>(relaxed = true)

    init {
        System.setProperty("KAFKA_SCHEMA_REGISTRY", "KAFKA_SCHEMA_REGISTRY")
        System.setProperty("KAFKA_SCHEMA_REGISTRY_USER", "KAFKA_SCHEMA_REGISTRY_USER")
        System.setProperty("KAFKA_SCHEMA_REGISTRY_PASSWORD", "KAFKA_SCHEMA_REGISTRY_PASSWORD")
        System.setProperty("KAFKA_BROKERS", "KAFKA_BROKERS")

        MeldestatusMottak(testRapid, meldestatusMediator, meldestatusMetrikker, meldingerRepository)
    }

    @BeforeEach
    fun setUp() {
        testRapid.reset()
    }

    @Test
    fun `onPacket behandler melding, lagrer den og inkrementerer mottakmetrikk`() {
        val metrikkCount = meldestatusMetrikker.meldestatusMottatt.count()

        testRapid.sendTestMessage(lagMeldestatusEndringEvent())

        val forventetHendelse =
            MeldestatusHendelse(
                personId = 5268057,
                meldestatusId = 95,
                hendelseId = 95,
            )

        verify(exactly = 1) { meldestatusMediator.behandle(forventetHendelse) }
        coVerify(exactly = 1) {
            meldingerRepository.lagreInnkommendeMelding(
                korrelasjonsId = any(),
                ident = null,
                relevantMeldingsinnhold = defaultObjectMapper.writeValueAsString(forventetHendelse),
            )
        }
        meldestatusMetrikker.meldestatusMottatt.count() shouldBe metrikkCount + 1
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
