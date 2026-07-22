package no.nav.dagpenger.rapportering.personregister.modell.utils

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

class DatoUtilsTest {
    private val nå = LocalDate.now()
    private val iGår = nå.minusDays(1)
    private val iMorgen = nå.plusDays(1)
    private val enMånedSiden = nå.minusMonths(1)
    private val enMånedFrem = nå.plusMonths(1)

    @Nested
    inner class ErFortid {
        @Test
        fun `returnerer false når LocalDateTime er null`() {
            val nullDateTime: LocalDateTime? = null
            nullDateTime.erFortid() shouldBe false
        }

        @Test
        fun `returnerer true når LocalDateTime er i fortiden`() {
            val fortid = LocalDateTime.now().minusDays(1)
            fortid.erFortid() shouldBe true
        }

        @Test
        fun `returnerer true når LocalDateTime er flere år i fortiden`() {
            val fortid = LocalDateTime.now().minusYears(5)
            fortid.erFortid() shouldBe true
        }

        @Test
        fun `returnerer false når LocalDateTime er i dag`() {
            val iDag = LocalDateTime.now().withHour(12).withMinute(0)
            iDag.erFortid() shouldBe false
        }

        @Test
        fun `returnerer false når LocalDateTime er i fremtiden`() {
            val fremtid = LocalDateTime.now().plusDays(1)
            fremtid.erFortid() shouldBe false
        }

        @Test
        fun `returnerer false når LocalDateTime er flere år i fremtiden`() {
            val fremtid = LocalDateTime.now().plusYears(5)
            fremtid.erFortid() shouldBe false
        }
    }

    @Nested
    inner class ErFortidEllerIdag {
        @Test
        fun `returnerer true når LocalDate er i fortiden`() {
            iGår.erFortidEllerIdag() shouldBe true
        }

        @Test
        fun `returnerer true når LocalDate er en måned i fortiden`() {
            enMånedSiden.erFortidEllerIdag() shouldBe true
        }

        @Test
        fun `returnerer true når LocalDate er i dag`() {
            nå.erFortidEllerIdag() shouldBe true
        }

        @Test
        fun `returnerer false når LocalDate er i morgen`() {
            iMorgen.erFortidEllerIdag() shouldBe false
        }

        @Test
        fun `returnerer false når LocalDate er i fremtiden`() {
            enMånedFrem.erFortidEllerIdag() shouldBe false
        }

        @Test
        fun `returnerer false når LocalDate er mange år i fremtiden`() {
            val fjerntFremtid = nå.plusYears(10)
            fjerntFremtid.erFortidEllerIdag() shouldBe false
        }
    }

    @Nested
    inner class ErIdagEllerIFremtid {
        @Test
        fun `returnerer false når LocalDate er i fortiden`() {
            iGår.erIdagEllerIFremtid() shouldBe false
        }

        @Test
        fun `returnerer false når LocalDate er en måned i fortiden`() {
            enMånedSiden.erIdagEllerIFremtid() shouldBe false
        }

        @Test
        fun `returnerer true når LocalDate er i dag`() {
            nå.erIdagEllerIFremtid() shouldBe true
        }

        @Test
        fun `returnerer true når LocalDate er i morgen`() {
            iMorgen.erIdagEllerIFremtid() shouldBe true
        }

        @Test
        fun `returnerer true når LocalDate er i fremtiden`() {
            enMånedFrem.erIdagEllerIFremtid() shouldBe true
        }

        @Test
        fun `returnerer true når LocalDate er mange år i fremtiden`() {
            val fjerntFremtid = nå.plusYears(10)
            fjerntFremtid.erIdagEllerIFremtid() shouldBe true
        }
    }
}
