package no.nav.dagpenger.rapportering.personregister.mediator.service

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class OvertaArbeidssøkerBekreftelseTest {
    private val testRapid = TestRapid()
    private val overtaBekreftelse = OvertaArbeidssøkerBekreftelse(testRapid)

    private val ident = "12345678910"

    @Test
    fun `skal overta arbeidssøkerstatus for en periode`() {
        val periodeId = "periode123"

        overtaBekreftelse.behandle(ident, periodeId)

        with(testRapid.inspektør) {
            size shouldBe 1
            message(0)["ident"].asText() shouldBe ident
            message(0)["periodeId"].asText() shouldBe periodeId
        }
    }

    @Test
    fun `skal overta arbeidssøkerstatus for flere perioder`() {
        overtaBekreftelse.behandle(ident, "periode123")
        overtaBekreftelse.behandle(ident, "periode456")

        with(testRapid.inspektør) {
            size shouldBe 2
            message(0)["ident"].asText() shouldBe ident
            message(0)["periodeId"].asText() shouldBe "periode123"
            message(1)["ident"].asText() shouldBe ident
            message(1)["periodeId"].asText() shouldBe "periode456"
        }
    }
}
