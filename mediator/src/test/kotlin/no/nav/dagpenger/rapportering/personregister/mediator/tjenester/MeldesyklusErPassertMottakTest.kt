package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import no.nav.dagpenger.rapportering.personregister.mediator.PersonMediator
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.MeldesyklusErPassertHendelse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

class MeldesyklusErPassertMottakTest {
    private val testRapid = TestRapid()
    private val personMediator = mockk<PersonMediator>(relaxed = true)

    init {
        MeldesyklusErPassertMottak(testRapid, personMediator)
    }

    @BeforeEach
    fun setup() {
        testRapid.reset()
    }

    @Test
    fun `skal motta meldesyklus er passert event`() {
        val hendelseSlot = slot<MeldesyklusErPassertHendelse>()
        every { personMediator.behandle(capture(hendelseSlot), 1) } just runs

        val ident = "12345678903"
        val dato = LocalDate.now()
        val referanseId = UUID.randomUUID().toString()
        val meldekortregisterPeriodeId = UUID.randomUUID().toString()
        val periodeFraOgMed = LocalDate.now().minusDays(35)
        val periodeTilOgMed = LocalDate.now().minusDays(21)

        val melding =
            """
            {
              "@event_name": "meldesyklus_er_passert",
              "ident": "$ident",
              "dato": "$dato",
              "referanseId": "$referanseId",
              "meldekortregisterPeriodeId": "$meldekortregisterPeriodeId",
              "periodeFraOgMed": "$periodeFraOgMed",
              "periodeTilOgMed": "$periodeTilOgMed"
            }
            """.trimIndent()

        testRapid.sendTestMessage(melding)

        hendelseSlot.captured.ident shouldBe ident
        hendelseSlot.captured.dato.toLocalDate() shouldBe dato
        hendelseSlot.captured.startDato.toLocalDate() shouldBe dato
        hendelseSlot.captured.referanseId shouldBe referanseId
        hendelseSlot.captured.meldekortregisterPeriodeId shouldBe meldekortregisterPeriodeId
        hendelseSlot.captured.periodeFraOgMed shouldBe periodeFraOgMed
        hendelseSlot.captured.periodeTilOgMed shouldBe periodeTilOgMed
    }

    @Test
    fun `skal hoppe over meldinger med feil ident`() {
        val ident = "12345"
        val dato = LocalDate.now()
        val referanseId = UUID.randomUUID().toString()
        val meldekortregisterPeriodeId = UUID.randomUUID().toString()
        val periodeFraOgMed = LocalDate.now().minusDays(35)
        val periodeTilOgMed = LocalDate.now().minusDays(21)

        val melding =
            """
            {
              "@event_name": "meldesyklus_er_passert",
              "ident": "$ident",
              "dato": "$dato",
              "referanseId": "$referanseId",
              "meldekortregisterPeriodeId": "$meldekortregisterPeriodeId",
              "periodeFraOgMed": "$periodeFraOgMed",
              "periodeTilOgMed": "$periodeTilOgMed"
            }
            """.trimIndent()

        testRapid.sendTestMessage(melding)

        verify(exactly = 0) { personMediator.behandle(any<MeldesyklusErPassertHendelse>()) }
    }
}
