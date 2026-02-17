package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import no.nav.dagpenger.rapportering.personregister.mediator.PersonMediator
import no.nav.dagpenger.rapportering.personregister.mediator.utils.MetrikkerTestUtil.meldesyklusErPassertMetrikker
import no.nav.dagpenger.rapportering.personregister.mediator.utils.UUIDv7
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.MeldesyklusErPassertHendelse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

class MeldesyklusErPassertMottakTest {
    private val testRapid = TestRapid()
    private val personMediator = mockk<PersonMediator>(relaxed = true)

    init {
        MeldesyklusErPassertMottak(testRapid, personMediator, meldesyklusErPassertMetrikker)
    }

    @BeforeEach
    fun setup() {
        testRapid.reset()
    }

    @Test
    fun `onPacket behandler melding og inkrementerer metrikk`() {
        val metrikkCount = meldesyklusErPassertMetrikker.meldesyklusErPassertMottatt.count()
        val hendelseSlot = slot<MeldesyklusErPassertHendelse>()
        every { personMediator.behandle(capture(hendelseSlot), 1) } just runs

        val ident = "12345678903"
        val dato = LocalDate.now()
        val referanseId = UUIDv7.newUuid().toString()
        val meldekortregisterPeriodeId = UUIDv7.newUuid().toString()
        val periodeFraOgMed = LocalDate.now().minusDays(35)
        val periodeTilOgMed = LocalDate.now().minusDays(21)

        testRapid.sendTestMessage(
            lagMelding(
                ident,
                dato,
                referanseId,
                meldekortregisterPeriodeId,
                periodeFraOgMed,
                periodeTilOgMed,
            ),
        )

        hendelseSlot.captured.ident shouldBe ident
        hendelseSlot.captured.dato.toLocalDate() shouldBe dato
        hendelseSlot.captured.startDato.toLocalDate() shouldBe dato
        hendelseSlot.captured.referanseId shouldBe referanseId
        meldesyklusErPassertMetrikker.meldesyklusErPassertMottatt.count() shouldBe metrikkCount + 1
    }

    @Test
    fun `onPacket kaster exception og inkrementerer metrikk hvis ident ikke validerer`() {
        val metrikkCount = meldesyklusErPassertMetrikker.meldesyklusErPassertFeilet.count()

        shouldThrow<IllegalArgumentException> {
            testRapid.sendTestMessage(lagMelding("12345"))
        }
        meldesyklusErPassertMetrikker.meldesyklusErPassertFeilet.count() shouldBe metrikkCount + 1
    }

    @Test
    fun `onPacket kaster exception og inkrementerer metrikk hvis behandling feiler`() {
        val metrikkCount = meldesyklusErPassertMetrikker.meldesyklusErPassertFeilet.count()
        every { personMediator.behandle(any<MeldesyklusErPassertHendelse>()) } throws RuntimeException("kaboom")

        val exception =
            shouldThrow<RuntimeException> {
                testRapid.sendTestMessage(lagMelding())
            }

        exception.message shouldBe "kaboom"
        meldesyklusErPassertMetrikker.meldesyklusErPassertFeilet.count() shouldBe metrikkCount + 1
    }

    private fun lagMelding(
        ident: String = "09876543210",
        dato: LocalDate? = LocalDate.now(),
        referanseId: String = UUIDv7.newUuid().toString(),
        meldekortregisterPeriodeId: String = UUIDv7.newUuid().toString(),
        periodeFraOgMed: LocalDate? = LocalDate.now().minusDays(35),
        periodeTilOgMed: LocalDate? = LocalDate.now().minusDays(21),
    ): String =
        //language=json
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
}
