package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import com.fasterxml.jackson.module.kotlin.readValue
import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.matchers.shouldBe
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import no.nav.dagpenger.rapportering.personregister.mediator.Configuration.defaultObjectMapper
import no.nav.dagpenger.rapportering.personregister.mediator.PersonMediator
import no.nav.dagpenger.rapportering.personregister.mediator.db.MeldingerRepository
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.NødbremsHendelse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

class NødbremsMottakTest {
    private val testRapid = TestRapid()
    private val personMediator = mockk<PersonMediator>(relaxed = true)
    private val meldingerRepository = mockk<MeldingerRepository>(relaxed = true)

    init {
        System.setProperty("KAFKA_SCHEMA_REGISTRY", "KAFKA_SCHEMA_REGISTRY")
        System.setProperty("KAFKA_SCHEMA_REGISTRY_USER", "KAFKA_SCHEMA_REGISTRY_USER")
        System.setProperty("KAFKA_SCHEMA_REGISTRY_PASSWORD", "KAFKA_SCHEMA_REGISTRY_PASSWORD")
        System.setProperty("KAFKA_BROKERS", "KAFKA_BROKERS")

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

        coVerify(exactly = 1) {
            meldingerRepository.lagreInnkommendeMelding(
                korrelasjonsId = any(),
                ident = ident,
                relevantMeldingsinnhold =
                    match { melding ->
                        with(defaultObjectMapper.readValue<NødbremsHendelse>(melding)) {
                            this.ident == ident &&
                                this.dato.toLocalDate() == dato &&
                                this.startDato.toLocalDate() == dato
                        }
                    },
            )
        }
    }
}
