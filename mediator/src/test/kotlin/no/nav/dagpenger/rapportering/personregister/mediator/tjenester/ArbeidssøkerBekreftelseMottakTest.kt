package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import com.fasterxml.jackson.module.kotlin.readValue
import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.matchers.shouldBe
import io.mockk.coVerify
import io.mockk.mockk
import no.nav.dagpenger.rapportering.personregister.mediator.Configuration.defaultObjectMapper
import no.nav.dagpenger.rapportering.personregister.mediator.db.MeldingerRepository
import no.nav.dagpenger.rapportering.personregister.mediator.service.ArbeidssøkerBekreftelseService
import no.nav.dagpenger.rapportering.personregister.mediator.utils.MetrikkerTestUtil.arbeidssøkerBekreftelseFraDpMeldekortregisterMetrikker
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ArbeidssøkerBekreftelseMottakTest {
    private val testRapid = TestRapid()
    private val arbeidssøkerBekreftelseService = mockk<ArbeidssøkerBekreftelseService>(relaxed = true)
    private val meldingerRepository = mockk<MeldingerRepository>(relaxed = true)

    init {
        System.setProperty("KAFKA_SCHEMA_REGISTRY", "KAFKA_SCHEMA_REGISTRY")
        System.setProperty("KAFKA_SCHEMA_REGISTRY_USER", "KAFKA_SCHEMA_REGISTRY_USER")
        System.setProperty("KAFKA_SCHEMA_REGISTRY_PASSWORD", "KAFKA_SCHEMA_REGISTRY_PASSWORD")
        System.setProperty("KAFKA_BROKERS", "KAFKA_BROKERS")

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
    fun `mottar og sender riktig melding til service, lagrer meldingene og øker metrikk`() {
        val metrikkCount = arbeidssøkerBekreftelseFraDpMeldekortregisterMetrikker.arbeidssøkerbekreftelseMottatt.count()

        testRapid.sendTestMessage(ArbeidssøkerBekreftelseTestData.event())

        coVerify(exactly = 1) {
            arbeidssøkerBekreftelseService.behandle(
                match { melding ->
                    melding.ident == ArbeidssøkerBekreftelseTestData.IDENT &&
                        melding.bekreftelse.periodeId == ArbeidssøkerBekreftelseTestData.periodeId &&
                        melding.bekreftelse.id == ArbeidssøkerBekreftelseTestData.bekreftelseId &&
                        melding.bekreftelse.svar.harJobbetIDennePerioden &&
                        melding.bekreftelse.svar.vilFortsetteSomArbeidssøker
                },
            )
        }

        coVerify(exactly = 1) {
            meldingerRepository.lagreInnkommendeMelding(
                korrelasjonsId = any(),
                ident = ArbeidssøkerBekreftelseTestData.IDENT,
                relevantMeldingsinnhold =
                    match { melding ->
                        with(defaultObjectMapper.readValue<ArbeidssøkerBekreftelseMelding>(melding)) {
                            this.ident == ArbeidssøkerBekreftelseTestData.IDENT &&
                                this.bekreftelse.periodeId == ArbeidssøkerBekreftelseTestData.periodeId &&
                                this.bekreftelse.id == ArbeidssøkerBekreftelseTestData.bekreftelseId &&
                                this.bekreftelse.svar.harJobbetIDennePerioden &&
                                this.bekreftelse.svar.vilFortsetteSomArbeidssøker
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

        coVerify(exactly = 0) { arbeidssøkerBekreftelseService.behandle(any()) }
    }
}
