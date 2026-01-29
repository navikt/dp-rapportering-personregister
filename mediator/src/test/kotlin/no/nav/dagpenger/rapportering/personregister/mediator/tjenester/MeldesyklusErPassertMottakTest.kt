package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import no.nav.dagpenger.rapportering.personregister.mediator.PersonMediator
import no.nav.dagpenger.rapportering.personregister.mediator.utils.UUIDv7
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.MeldesyklusErPassertHendelse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate

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
        val referanseId = UUIDv7.newUuid().toString()
        val meldekortregisterPeriodeId = UUIDv7.newUuid().toString()
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
    }

    @Test
    fun `skal kaste Exception ved feil i ident`() {
        val ident = "12345"
        val dato = LocalDate.now()
        val referanseId = UUIDv7.newUuid().toString()
        val meldekortregisterPeriodeId = UUIDv7.newUuid().toString()
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

        assertThrows<IllegalArgumentException>({ testRapid.sendTestMessage(melding) })
    }
}
