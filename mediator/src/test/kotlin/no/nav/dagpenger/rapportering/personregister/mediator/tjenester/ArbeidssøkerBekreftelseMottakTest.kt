package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.matchers.shouldBe
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import no.nav.dagpenger.rapportering.personregister.mediator.db.MeldingerRepository
import no.nav.dagpenger.rapportering.personregister.mediator.service.ArbeidssøkerBekreftelseService
import no.nav.dagpenger.rapportering.personregister.mediator.utils.MetrikkerTestUtil.arbeidssøkerBekreftelseFraDpMeldekortregisterMetrikker
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tools.jackson.databind.ObjectMapper

class ArbeidssøkerBekreftelseMottakTest {
    private val testRapid = TestRapid()
    private val arbeidssøkerBekreftelseService = mockk<ArbeidssøkerBekreftelseService>(relaxed = true)
    private val meldingerRepository = mockk<MeldingerRepository>(relaxed = true)

    init {
        ArbeidssøkerBekreftelseMottak(
            testRapid,
            arbeidssøkerBekreftelseService,
            arbeidssøkerBekreftelseFraDpMeldekortregisterMetrikker,
            meldingerRepository,
        )
    }

    @BeforeEach
    fun setup() {
        testRapid.reset()
    }

    @Test
    fun `mottar og sender riktig melding til service og øker metrikk`() {
        val metrikkCount = arbeidssøkerBekreftelseFraDpMeldekortregisterMetrikker.arbeidssøkerbekreftelseMottatt.count()

        testRapid.sendTestMessage(ArbeidssøkerBekreftelseTestData.event())

        coVerify(exactly = 1) {
            arbeidssøkerBekreftelseService.behandle(
                match { melding ->
                    melding.ident == ArbeidssøkerBekreftelseTestData.ident &&
                        melding.bekreftelse.periodeId == ArbeidssøkerBekreftelseTestData.periodeId &&
                        melding.bekreftelse.id == ArbeidssøkerBekreftelseTestData.bekreftelseId &&
                        melding.bekreftelse.svar.harJobbetIDennePerioden &&
                        melding.bekreftelse.svar.vilFortsetteSomArbeidssøker
                },
                any(),
            )
        }

        verify(exactly = 1) {
            meldingerRepository.lagreInnkommendeMelding(
                any(),
                ArbeidssøkerBekreftelseTestData.ident,
                match { melding ->
                    with(ObjectMapper().readTree(melding)) {
                        this["@event_name"].asString() == "arbeidssøkerbekreftelse" &&
                            with(this["arbeidssøkerBekreftelseMelding"]["bekreftelse"]) {
                                this["periodeId"].asString() == ArbeidssøkerBekreftelseTestData.periodeId.toString() &&
                                    this["id"].asString() == ArbeidssøkerBekreftelseTestData.bekreftelseId.toString() &&
                                    this["svar"]["harJobbetIDennePerioden"].asBoolean() &&
                                    this["svar"]["vilFortsetteSomArbeidssøker"].asBoolean()
                            }
                    }
                },
            )
        }

        arbeidssøkerBekreftelseFraDpMeldekortregisterMetrikker.arbeidssøkerbekreftelseMottatt.count() shouldBe metrikkCount + 1
    }

    @Test
    fun `tar ikke imot meldinger som mangler påkrevde felter`() {
        testRapid.sendTestMessage(
            """
            {
              "@event_name": "arbeidssøkerbekreftelse",
              "bekreftelse": {}
            }
            """.trimIndent(),
        )

        coVerify(exactly = 0) { arbeidssøkerBekreftelseService.behandle(any(), any()) }
    }
}
