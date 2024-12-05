package no.nav.dagpenger.rapportering.personregister.mediator.service

import io.kotest.matchers.shouldBe
import no.nav.dagpenger.rapportering.personregister.mediator.kafka.MockKafkaProducer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.BeforeTest

class OvertaArbeidssøkerBekreftelseTest {
    private var mockProdusent = MockKafkaProducer<OvertaArbeidssøkerBekreftelseMelding>()

    private lateinit var overtaBekreftelse: OvertaArbeidssøkerBekreftelse

    @BeforeTest
    fun setup() {
        overtaBekreftelse = OvertaArbeidssøkerBekreftelse(mockProdusent)
    }

    @BeforeEach
    fun reset() {
        mockProdusent.reset()
    }

    @Test
    fun `skal overta arbeidssøkerstatus for en periode`() {
        val periodeId = "periode123"

        overtaBekreftelse.behandle(periodeId)

        val meldinger = mockProdusent.meldinger
        val melding = meldinger.first()

        meldinger.size shouldBe 1
        with(melding) {
            periodeId shouldBe periodeId
            bekreftelsesLøsning shouldBe OvertaArbeidssøkerBekreftelseMelding.BekreftelsesLøsning.DAGPENGER
            start.intervalMS shouldBe dagerTilMillisekunder(14)
            start.graceMS shouldBe dagerTilMillisekunder(8)
        }
    }

    @Test
    fun `skal overta arbeidssøkerstatus for flere perioder`() {
        overtaBekreftelse.behandle("periode123")
        overtaBekreftelse.behandle("periode456")

        val meldinger = mockProdusent.meldinger
        assertEquals(2, meldinger.size)

        with(meldinger) {
            get(0).periodeId shouldBe "periode123"
            get(1).periodeId shouldBe "periode456"
        }
    }
}

fun dagerTilMillisekunder(dager: Long): Long = dager * 24 * 60 * 60 * 1000
