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
import no.nav.dagpenger.rapportering.personregister.mediator.db.MeldingerRepository
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.NødbremsHendelse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tools.jackson.databind.ObjectMapper
import java.time.LocalDate

class NødbremsMottakTest {
    private val testRapid = TestRapid()
    private val personMediator = mockk<PersonMediator>(relaxed = true)
    private val meldingerRepository = mockk<MeldingerRepository>(relaxed = true)

    init {
        NødbremsMottak(testRapid, personMediator, meldingerRepository)
    }

    @BeforeEach
    fun setup() {
        testRapid.reset()
    }

    @Test
    fun `skal motta nødbrems event`() {
        val hendelseSlot = slot<NødbremsHendelse>()
        every { personMediator.behandle(capture(hendelseSlot)) } just runs

        val ident = "12345678903"
        val dato = LocalDate.now()

        val nødbremsMelding =
            """
            {
              "@event_name": "ramps_nødbrems",
              "ident": "$ident"
            }
            """.trimIndent()

        testRapid.sendTestMessage(nødbremsMelding)

        hendelseSlot.captured.ident shouldBe ident
        hendelseSlot.captured.dato.toLocalDate() shouldBe dato
        hendelseSlot.captured.startDato.toLocalDate() shouldBe dato

        verify(exactly = 1) { personMediator.behandle(any<NødbremsHendelse>()) }
        verify(exactly = 1) {
            meldingerRepository.lagreInnkommendeMelding(
                any(),
                ident,
                match { melding ->
                    with(ObjectMapper().readTree(melding)) {
                        this["@event_name"].asString() == "ramps_nødbrems"
                    }
                },
            )
        }
    }
}
