package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import no.nav.dagpenger.rapportering.personregister.mediator.PersonMediator
import no.nav.dagpenger.rapportering.personregister.mediator.db.MeldingerRepository
import no.nav.dagpenger.rapportering.personregister.mediator.utils.MetrikkerTestUtil.ikkeMeldtSegPå21DagerMetrikker
import no.nav.dagpenger.rapportering.personregister.mediator.utils.UUIDv7
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.IkkeMeldtSegPå21DagerHendelse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tools.jackson.databind.ObjectMapper
import java.time.LocalDate

class IkkeMeldtSegPå21DagerMottakTest {
    private val testRapid = TestRapid()
    private val personMediator = mockk<PersonMediator>(relaxed = true)
    private val meldingerRepository = mockk<MeldingerRepository>(relaxed = true)

    init {
        IkkeMeldtSegPå21DagerMottak(testRapid, personMediator, ikkeMeldtSegPå21DagerMetrikker, meldingerRepository)
    }

    @BeforeEach
    fun setup() {
        testRapid.reset()
    }

    @Test
    fun `onPacket behandler melding og inkrementerer metrikk`() {
        val metrikkCount = ikkeMeldtSegPå21DagerMetrikker.ikkeMeldtSegPå21DagerMottatt.count()
        val hendelseSlot = slot<IkkeMeldtSegPå21DagerHendelse>()
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
        ikkeMeldtSegPå21DagerMetrikker.ikkeMeldtSegPå21DagerMottatt.count() shouldBe metrikkCount + 1

        verify(exactly = 1) {
            meldingerRepository.lagreInnkommendeMelding(
                any(),
                ident,
                match { melding ->
                    with(ObjectMapper().readTree(melding)) {
                        this["@event_name"].asString() == "ikke_meldt_seg_på_21_dager" &&
                            this["referanseId"].asString() == referanseId
                    }
                },
            )
        }
    }

    @Test
    fun `onPacket kaster exception og inkrementerer metrikk hvis ident ikke validerer`() {
        val metrikkCount = ikkeMeldtSegPå21DagerMetrikker.ikkeMeldtSegPå21DagerFeilet.count()

        shouldThrow<IllegalArgumentException> {
            testRapid.sendTestMessage(lagMelding("12345"))
        }
        ikkeMeldtSegPå21DagerMetrikker.ikkeMeldtSegPå21DagerFeilet.count() shouldBe metrikkCount + 1
        verify(exactly = 0) { meldingerRepository.lagreInnkommendeMelding(any(), any(), any()) }
    }

    @Test
    fun `onPacket kaster exception og inkrementerer metrikk hvis behandling feiler`() {
        val ident = "12345678903"
        val referanseId = UUIDv7.newUuid().toString()

        val metrikkCount = ikkeMeldtSegPå21DagerMetrikker.ikkeMeldtSegPå21DagerFeilet.count()
        every { personMediator.behandle(any<IkkeMeldtSegPå21DagerHendelse>()) } throws RuntimeException("kaboom")

        val exception =
            shouldThrow<RuntimeException> {
                testRapid.sendTestMessage(
                    lagMelding(
                        ident = ident,
                        referanseId = referanseId,
                    ),
                )
            }

        exception.message shouldBe "kaboom"
        ikkeMeldtSegPå21DagerMetrikker.ikkeMeldtSegPå21DagerFeilet.count() shouldBe metrikkCount + 1

        verify(exactly = 1) {
            meldingerRepository.lagreInnkommendeMelding(
                any(),
                ident,
                match { melding ->
                    with(ObjectMapper().readTree(melding)) {
                        this["@event_name"].asString() == "ikke_meldt_seg_på_21_dager" &&
                            this["referanseId"].asString() == referanseId
                    }
                },
            )
        }
    }

    @Test
    fun `onPacket behandler legacy meldekort_er_passert hendelse`() {
        val ident = "12345678903"
        val referanseId = UUIDv7.newUuid().toString()

        testRapid.sendTestMessage(
            lagMelding(
                ident = ident,
                referanseId = referanseId,
                event = "meldesyklus_er_passert",
            ),
        )

        verify { personMediator.behandle(any<IkkeMeldtSegPå21DagerHendelse>(), 1) }
        verify(exactly = 1) {
            meldingerRepository.lagreInnkommendeMelding(
                any(),
                ident,
                match { melding ->
                    with(ObjectMapper().readTree(melding)) {
                        this["@event_name"].asString() == "meldesyklus_er_passert" &&
                            this["referanseId"].asString() == referanseId
                    }
                },
            )
        }
    }

    private fun lagMelding(
        ident: String = "09876543210",
        dato: LocalDate? = LocalDate.now(),
        referanseId: String = UUIDv7.newUuid().toString(),
        meldekortregisterPeriodeId: String = UUIDv7.newUuid().toString(),
        periodeFraOgMed: LocalDate? = LocalDate.now().minusDays(35),
        periodeTilOgMed: LocalDate? = LocalDate.now().minusDays(21),
        event: String = "ikke_meldt_seg_på_21_dager",
    ): String =
        //language=json
        """
        {
          "@event_name": "$event",
          "ident": "$ident",
          "dato": "$dato",
          "referanseId": "$referanseId",
          "meldekortregisterPeriodeId": "$meldekortregisterPeriodeId",
          "periodeFraOgMed": "$periodeFraOgMed",
          "periodeTilOgMed": "$periodeTilOgMed"
        }
        """.trimIndent()
}
