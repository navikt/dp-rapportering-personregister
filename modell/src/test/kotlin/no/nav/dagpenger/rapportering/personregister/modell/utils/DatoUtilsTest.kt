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
    inner class ErDatoIFortid {
        @Test
        fun `returnerer false når LocalDateTime er null`() {
            val nullDateTime: LocalDateTime? = null
            nullDateTime.erDatoIFortid() shouldBe false
        }

        @Test
        fun `returnerer true når LocalDateTime er i fortid`() {
            val fortid = LocalDateTime.now().minusDays(1)
            fortid.erDatoIFortid() shouldBe true
        }

        @Test
        fun `returnerer true når LocalDateTime er flere år i fortid`() {
            val fortid = LocalDateTime.now().minusYears(5)
            fortid.erDatoIFortid() shouldBe true
        }

        @Test
        fun `returnerer false når LocalDateTime er i dag`() {
            val iDag = LocalDateTime.now().withHour(12).withMinute(0)
            iDag.erDatoIFortid() shouldBe false
        }

        @Test
        fun `returnerer false når LocalDateTime er tidligere samme dag`() {
            val iDag =
                LocalDateTime
                    .now()
                    .withHour(0)
                    .withMinute(0)
                    .withSecond(0)
            iDag.erDatoIFortid() shouldBe false
        }

        @Test
        fun `returnerer false når LocalDateTime er seinere samme dag`() {
            val iDag =
                LocalDateTime
                    .now()
                    .withHour(23)
                    .withMinute(59)
                    .withSecond(57)
            iDag.erDatoIFortid() shouldBe false
        }

        @Test
        fun `returnerer false når LocalDateTime er i fremtiden`() {
            val fremtid = LocalDateTime.now().plusDays(1)
            fremtid.erDatoIFortid() shouldBe false
        }

        @Test
        fun `returnerer false når LocalDateTime er flere år i fremtiden`() {
            val fremtid = LocalDateTime.now().plusYears(5)
            fremtid.erDatoIFortid() shouldBe false
        }
    }

    @Nested
    inner class ErIFortid {
        @Test
        fun `returnerer false når LocalDateTime er null`() {
            val nullDateTime: LocalDateTime? = null
            nullDateTime.erIFortid() shouldBe false
        }

        @Test
        fun `returnerer true når LocalDateTime er i fortid`() {
            val fortid = LocalDateTime.now().minusSeconds(1)
            fortid.erIFortid() shouldBe true
        }

        @Test
        fun `returnerer false når LocalDateTime er i fremtiden`() {
            val fremtid = LocalDateTime.now().plusSeconds(1)
            fremtid.erIFortid() shouldBe false
        }
    }

    @Nested
    inner class ErIFremtid {
        @Test
        fun `returnerer false når LocalDateTime er null`() {
            val nullDateTime: LocalDateTime? = null
            nullDateTime.erIFremtid() shouldBe false
        }

        @Test
        fun `returnerer false når LocalDateTime er i fortid`() {
            val fortid = LocalDateTime.now().minusSeconds(1)
            fortid.erIFremtid() shouldBe false
        }

        @Test
        fun `returnerer true når LocalDateTime er i fremtid`() {
            val fremtid = LocalDateTime.now().plusSeconds(1)
            fremtid.erIFremtid() shouldBe true
        }
    }

    @Nested
    inner class ErIFortidEllerIdag {
        @Test
        fun `returnerer true når LocalDate er i fortid`() {
            iGår.erIFortidEllerIdag() shouldBe true
        }

        @Test
        fun `returnerer true når LocalDate er en måned i fortid`() {
            enMånedSiden.erIFortidEllerIdag() shouldBe true
        }

        @Test
        fun `returnerer true når LocalDate er i dag`() {
            nå.erIFortidEllerIdag() shouldBe true
        }

        @Test
        fun `returnerer false når LocalDate er i morgen`() {
            iMorgen.erIFortidEllerIdag() shouldBe false
        }

        @Test
        fun `returnerer false når LocalDate er i fremtiden`() {
            enMånedFrem.erIFortidEllerIdag() shouldBe false
        }

        @Test
        fun `returnerer false når LocalDate er mange år i fremtiden`() {
            val fjerntFremtid = nå.plusYears(10)
            fjerntFremtid.erIFortidEllerIdag() shouldBe false
        }
    }

    @Nested
    inner class ErIdagEllerIFremtid {
        @Test
        fun `returnerer false når LocalDate er i fortid`() {
            iGår.erIdagEllerIFremtid() shouldBe false
        }

        @Test
        fun `returnerer false når LocalDate er en måned i fortid`() {
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
        fun `returnerer true når LocalDate er i fremtid`() {
            enMånedFrem.erIdagEllerIFremtid() shouldBe true
        }

        @Test
        fun `returnerer true når LocalDate er mange år i fremtid`() {
            val fjerntFremtid = nå.plusYears(10)
            fjerntFremtid.erIdagEllerIFremtid() shouldBe true
        }
    }
}
