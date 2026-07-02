package no.nav.dagpenger.rapportering.personregister.modell.hendelser

import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.mockk.verify
import no.nav.dagpenger.rapportering.personregister.modell.AnsvarligSystem
import no.nav.dagpenger.rapportering.personregister.modell.PersonObserver
import no.nav.dagpenger.rapportering.personregister.modell.helper.testPerson
import no.nav.dagpenger.rapportering.personregister.modell.helper.vedtakHendelse
import no.nav.dagpenger.rapportering.personregister.modell.helper.vedtakHendelseMedFremtidigStans
import org.junit.jupiter.api.Test
import java.time.LocalDate

class VedtakHendelseTest {
    private val nå = LocalDate.now().atStartOfDay()
    private val fortid = nå.minusDays(1)
    private val fremtid = nå.plusDays(1)

    @Test
    fun `hendelse med utfall true legges til person`() =
        testPerson {
            behandle(vedtakHendelse(utfall = true))

            hendelser.size shouldBe 1
            (hendelser.first() as VedtakHendelse).utfall shouldBe true
        }

    @Test
    fun `hendelse med utfall true og uten sluttDato setter harRettTilDp til true`() =
        testPerson {
            setHarRettTilDp(false)
            behandle(vedtakHendelse(utfall = true, sluttDato = null))

            harRettTilDp shouldBe true
        }

    @Test
    fun `hendelse med utfall true setter ansvarligSystem til DP`() =
        testPerson {
            setAnsvarligSystem(AnsvarligSystem.ARENA)
            behandle(vedtakHendelse(utfall = true))

            ansvarligSystem shouldBe AnsvarligSystem.DP
        }

    @Test
    fun `hendelse med utfall false setter harRettTilDp til false`() =
        testPerson {
            setHarRettTilDp(true)
            behandle(vedtakHendelse(utfall = false))

            harRettTilDp shouldBe false
        }

    @Test
    fun `hendelse med utfall true og sluttDato i fortid setter harRettTilDp til false`() =
        testPerson {
            behandle(vedtakHendelse(utfall = true, startDato = fortid, sluttDato = fortid))

            harRettTilDp shouldBe false
        }

    @Test
    fun `hendelse med utfall true sender startmelding med skalMigreres true når ansvarligSystem er ARENA`() {
        val observer = mockk<PersonObserver>(relaxed = true)
        testPerson {
            setAnsvarligSystem(AnsvarligSystem.ARENA)
            addObserver(observer)
            behandle(vedtakHendelse(utfall = true))

            verify(exactly = 1) {
                observer.sendStartMeldingTilMeldekortregister(any(), any(), any(), skalMigreres = true)
            }
        }
    }

    @Test
    fun `hendelse med utfall true sender startmelding med skalMigreres false når ansvarligSystem allerede er DP`() {
        val observer = mockk<PersonObserver>(relaxed = true)
        testPerson {
            addObserver(observer)
            setAnsvarligSystem(AnsvarligSystem.DP)
            behandle(vedtakHendelse(utfall = true))

            verify(exactly = 1) {
                observer.sendStartMeldingTilMeldekortregister(any(), any(), any(), skalMigreres = false)
            }
        }
    }

    @Test
    fun `hendelse med utfall true og hvor hendelsen ikke gjelder fremtidig stans sender startmelding`() {
        val observer = mockk<PersonObserver>(relaxed = true)
        testPerson {
            addObserver(observer)
            behandle(vedtakHendelse(utfall = true))

            verify(exactly = 1) { observer.sendStartMeldingTilMeldekortregister(any(), any(), any(), any()) }
        }
    }

    @Test
    fun `hendelse med utfall false sender ikke startmelding`() {
        val observer = mockk<PersonObserver>(relaxed = true)
        testPerson {
            addObserver(observer)
            behandle(vedtakHendelse(utfall = false))

            verify(exactly = 0) { observer.sendStartMeldingTilMeldekortregister(any(), any(), any(), any()) }
        }
    }

    @Test
    fun `hendelse som gjelder fremtidig stans sender ikke startmelding når sluttDato er i fremtid og utfall er true`() {
        val observer = mockk<PersonObserver>(relaxed = true)
        testPerson {
            addObserver(observer)
            behandle(vedtakHendelseMedFremtidigStans(utfall = true, sluttDato = fremtid))

            verify(exactly = 0) { observer.sendStartMeldingTilMeldekortregister(any(), any(), any(), any()) }
        }
    }

    @Test
    fun `hendelse med utfall true og sluttDato i fortid sender startmelding og stoppmelding når hendelsen ikke gjelder fremtidig stans`() {
        val observer = mockk<PersonObserver>(relaxed = true)
        testPerson {
            addObserver(observer)
            behandle(vedtakHendelse(utfall = true, startDato = fortid, sluttDato = fortid))

            verify(exactly = 1) { observer.sendStartMeldingTilMeldekortregister(any(), any(), any(), any()) }
            verify(exactly = 1) { observer.sendStoppMeldingTilMeldekortregister(any(), any(), any()) }
        }
    }

    @Test
    fun `hendelse med utfall true og sluttDato i dag sender kun startmelding når hendelsen ikke gjelder fremtidig stans`() {
        val observer = mockk<PersonObserver>(relaxed = true)
        testPerson {
            addObserver(observer)
            behandle(vedtakHendelse(utfall = true, startDato = nå, sluttDato = nå))

            verify(exactly = 1) { observer.sendStartMeldingTilMeldekortregister(any(), any(), any(), any()) }
            verify(exactly = 0) { observer.sendStoppMeldingTilMeldekortregister(any(), any(), any()) }
        }
    }

    @Test
    fun `hendelse med utfall true og sluttDato i dag sender ingen melding når hendelsen gjelder fremtidig stans`() {
        val observer = mockk<PersonObserver>(relaxed = true)
        testPerson {
            addObserver(observer)
            behandle(vedtakHendelseMedFremtidigStans(utfall = true, startDato = nå, sluttDato = nå))

            verify(exactly = 0) { observer.sendStartMeldingTilMeldekortregister(any(), any(), any(), any()) }
            verify(exactly = 0) { observer.sendStoppMeldingTilMeldekortregister(any(), any(), any()) }
        }
    }

    @Test
    fun `hendelse med utfall true og sluttDato i fremtid sender kun startmelding, ikke stoppmelding`() {
        val observer = mockk<PersonObserver>(relaxed = true)
        testPerson {
            addObserver(observer)
            behandle(vedtakHendelse(utfall = true, startDato = nå, sluttDato = fremtid))

            verify(exactly = 1) { observer.sendStartMeldingTilMeldekortregister(any(), any(), any(), any()) }
            verify(exactly = 0) { observer.sendStoppMeldingTilMeldekortregister(any(), any(), any()) }
        }
    }

    @Test
    fun `hendelse med utfall true og ingen sluttDato sender kun startmelding, ikke stoppmelding`() {
        val observer = mockk<PersonObserver>(relaxed = true)
        testPerson {
            addObserver(observer)
            behandle(vedtakHendelse(utfall = true, sluttDato = null))

            verify(exactly = 1) { observer.sendStartMeldingTilMeldekortregister(any(), any(), any(), any()) }
            verify(exactly = 0) { observer.sendStoppMeldingTilMeldekortregister(any(), any(), any()) }
        }
    }

    @Test
    fun `hendelse med utfall false sender stoppmelding når ansvarligSystem er DP`() {
        val observer = mockk<PersonObserver>(relaxed = true)
        testPerson {
            addObserver(observer)
            setAnsvarligSystem(AnsvarligSystem.DP)
            behandle(vedtakHendelse(utfall = false))

            verify(exactly = 1) { observer.sendStoppMeldingTilMeldekortregister(any(), any(), any()) }
        }
    }

    @Test
    fun `hendelse med utfall false sender ikke stoppmelding når ansvarligSystem er ARENA`() {
        val observer = mockk<PersonObserver>(relaxed = true)
        testPerson {
            addObserver(observer)
            // ansvarligSystem er ARENA som standard
            behandle(vedtakHendelse(utfall = false))

            verify(exactly = 0) { observer.sendStoppMeldingTilMeldekortregister(any(), any(), any()) }
        }
    }

    @Test
    fun `erFremtidigStansHendelse returnerer true for hendelse laget med medFremtidigStopp`() {
        val hendelse = vedtakHendelseMedFremtidigStans(utfall = true)

        hendelse.erFremtidigStansHendelse() shouldBe true
    }

    @Test
    fun `erFremtidigStansHendelse returnerer false for vanlig VedtakHendelse`() {
        val hendelse = vedtakHendelse(utfall = true)

        hendelse.erFremtidigStansHendelse() shouldBe false
    }
}
