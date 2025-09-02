package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import no.nav.dagpenger.rapportering.personregister.mediator.FremtidigHendelseMediator
import no.nav.dagpenger.rapportering.personregister.mediator.PersonMediator
import no.nav.dagpenger.rapportering.personregister.mediator.utils.MetrikkerTestUtil.vedtakMetrikker
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.VedtakHendelse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

class VedtakMottakTest {
    private val testRapid = TestRapid()
    private val personMediator = mockk<PersonMediator>(relaxed = true)
    private val fremtidigHendelseMediator = mockk<FremtidigHendelseMediator>(relaxed = true)

    init {
        VedtakMottak(testRapid, personMediator, fremtidigHendelseMediator, vedtakMetrikker)
    }

    @BeforeEach
    fun setup() {
        testRapid.reset()
    }

    @Test
    fun `skal motta vedtak fattet event`() {
        val hendelseSlot = slot<VedtakHendelse>()
        every { personMediator.behandle(capture(hendelseSlot), 1) } just runs

        val ident = "12345678903"
        val behandlingId = "123e4567-e89b-12d3-a456-426614174000"
        val dato = LocalDate.now()

        val vedtakFattetMelding =
            """
            {
              "@event_name": "vedtak_fattet",
              "behandletHendelse": {
                "type": "Søknad",
                "id": "321e4567-e89b-12d3-a456-426614174000"
              },
              "behandlingId": "$behandlingId",
              "ident": "$ident",
              "virkningsdato": "$dato" ,
              "fastsatt": {
                 "status": "Innvilget"
                }
            }
            """.trimIndent()

        testRapid.sendTestMessage(vedtakFattetMelding)

        hendelseSlot.captured.ident shouldBe ident
        hendelseSlot.captured.dato.toLocalDate() shouldBe dato
        hendelseSlot.captured.startDato shouldBe dato.atStartOfDay()
        hendelseSlot.captured.referanseId shouldBe behandlingId
    }

    @Test
    fun `skal hoppe over vedtak med feil ident`() {
        val ident = "12345"
        val behandlingId = "123e4567-e89b-12d3-a456-426614174000"
        val dato = LocalDate.now()

        val vedtakFattetMelding =
            """
            {
              "@event_name": "vedtak_fattet",
              "behandletHendelse": {
                "type": "Søknad", 
                "id": "321e4567-e89b-12d3-a456-426614174000"
              "behandlingId": "$behandlingId",
              "ident": "$ident",
              "virkningsdato": "$dato",
               "fastsatt": {
                 "status": "Innvilget"
               }
            }
            """.trimIndent()

        testRapid.sendTestMessage(vedtakFattetMelding)

        verify(exactly = 0) { personMediator.behandle(any<VedtakHendelse>()) }
    }
}
