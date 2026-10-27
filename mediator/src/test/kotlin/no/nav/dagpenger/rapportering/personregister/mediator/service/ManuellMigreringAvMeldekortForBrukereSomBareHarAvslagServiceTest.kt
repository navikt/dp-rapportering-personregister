package no.nav.dagpenger.rapportering.personregister.mediator.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.observers.PersonObserverMeldekortregister
import no.nav.dagpenger.rapportering.personregister.mediator.utils.UUIDv7.newUuid
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.SøknadHendelse
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.VedtakHendelse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.LocalDateTime.now
import kotlin.time.Duration.Companion.milliseconds

class ManuellMigreringAvMeldekortForBrukereSomBareHarAvslagServiceTest {
    val tidspunktFeilenOppsto: LocalDateTime = LocalDateTime.parse("2026-08-31T09:00:00")
    val tidspunktFeilenBleFikset: LocalDateTime = LocalDateTime.parse("2026-10-16T14:35:00")
    val personRepository = mockk<PersonRepository>(relaxed = true)
    val personObserverMeldekortregister = mockk<PersonObserverMeldekortregister>(relaxed = true)
    lateinit var manuellMigreringAvMeldekortForBrukereSomBareHarAvslagService: ManuellMigreringAvMeldekortForBrukereSomBareHarAvslagService

    @BeforeEach
    fun setUp() {
        manuellMigreringAvMeldekortForBrukereSomBareHarAvslagService =
            ManuellMigreringAvMeldekortForBrukereSomBareHarAvslagService(
                personRepository,
                personObserverMeldekortregister,
            )
    }

    @Test
    fun `migrerMeldekort kaller ikke personObserverMeldekortregister hvis listen med personIder er tom`() {
        manuellMigreringAvMeldekortForBrukereSomBareHarAvslagService.migrerMeldekort(listOf(), 0.milliseconds)

        verify(exactly = 0) {
            personObserverMeldekortregister.sendStoppMeldingTilMeldekortregister(
                person = any(),
                fraOgMed = any(),
                tilOgMed = any(),
                harRett = any(),
                korrelasjonsId = any(),
            )
        }
    }

    @Test
    fun `migrerMeldekort kaller ikke personObserverMeldekortregister hvis personId ikke finnes`() {
        every { personRepository.hentPerson(any<Long>()) } returns null

        manuellMigreringAvMeldekortForBrukereSomBareHarAvslagService.migrerMeldekort(listOf(23L), 0.milliseconds)

        verify(exactly = 0) {
            personObserverMeldekortregister.sendStoppMeldingTilMeldekortregister(
                person = any(),
                fraOgMed = any(),
                tilOgMed = any(),
                harRett = any(),
                korrelasjonsId = any(),
            )
        }
    }

    @Test
    @Suppress("ktlint:standard:max-line-length")
    fun `migrerMeldekort kaller ikke personObserverMeldekortregister hvis bruker har minst en VedtakHendelse med utfall = true innenfor tidsrommet feilen oppsto`() {
        val person =
            Person("12345678901").apply {
                this.hendelser.addAll(
                    listOf(
                        lagVedtakhendelse(dato = tidspunktFeilenOppsto.plusMinutes(1), utfall = true),
                        lagVedtakhendelse(dato = tidspunktFeilenOppsto.plusMinutes(10), utfall = false),
                    ),
                )
            }
        every { personRepository.hentPerson(23L) } returns person

        manuellMigreringAvMeldekortForBrukereSomBareHarAvslagService.migrerMeldekort(listOf(23L), 0.milliseconds)

        verify(exactly = 0) {
            personObserverMeldekortregister.sendStoppMeldingTilMeldekortregister(
                person = any(),
                fraOgMed = any(),
                tilOgMed = any(),
                harRett = any(),
                korrelasjonsId = any(),
            )
        }
    }

    @Test
    @Suppress("ktlint:standard:max-line-length")
    fun `migrerMeldekort kaller ikke personObserverMeldekortregister hvis bruker har minst en VedtakHendelse med utfall = true utenfor tidsrommet feilen oppsto`() {
        val person =
            Person("12345678901").apply {
                this.hendelser.addAll(
                    listOf(
                        lagVedtakhendelse(dato = tidspunktFeilenOppsto.minusMinutes(1), utfall = true),
                        lagVedtakhendelse(dato = tidspunktFeilenOppsto.plusMinutes(10), utfall = false),
                    ),
                )
            }
        every { personRepository.hentPerson(23L) } returns person

        manuellMigreringAvMeldekortForBrukereSomBareHarAvslagService.migrerMeldekort(listOf(23L), 0.milliseconds)

        verify(exactly = 0) {
            personObserverMeldekortregister.sendStoppMeldingTilMeldekortregister(
                person = any(),
                fraOgMed = any(),
                tilOgMed = any(),
                harRett = any(),
                korrelasjonsId = any(),
            )
        }
    }

    @Test
    @Suppress("ktlint:standard:max-line-length")
    fun `migrerMeldekort kaller ikke personObserverMeldekortregister hvis bruker bare har VedtakHendelser med utfall = false som er mottatt utenfor tidsrommet feilen oppsto`() {
        val person =
            Person("12345678901").apply {
                this.hendelser.addAll(
                    listOf(
                        lagVedtakhendelse(dato = tidspunktFeilenOppsto.minusMinutes(1), utfall = false),
                        lagVedtakhendelse(dato = tidspunktFeilenBleFikset.plusMinutes(1), utfall = false),
                    ),
                )
            }
        every { personRepository.hentPerson(23L) } returns person

        manuellMigreringAvMeldekortForBrukereSomBareHarAvslagService.migrerMeldekort(listOf(23L), 0.milliseconds)

        verify(exactly = 0) {
            personObserverMeldekortregister.sendStoppMeldingTilMeldekortregister(
                person = any(),
                fraOgMed = any(),
                tilOgMed = any(),
                harRett = any(),
                korrelasjonsId = any(),
            )
        }
    }

    @Test
    @Suppress("ktlint:standard:max-line-length")
    fun `migrerMeldekort kaller personObserverMeldekortregister hvis bruker har VedtakHendelse med utfall = false mottat i tidsrommet feilen oppsto`() {
        val vedtakhendelse =
            lagVedtakhendelse(dato = tidspunktFeilenBleFikset.minusMinutes(1), utfall = false)
        val person =
            Person("12345678901").apply {
                this.hendelser.add(
                    vedtakhendelse,
                )
            }
        every { personRepository.hentPerson(23L) } returns person

        manuellMigreringAvMeldekortForBrukereSomBareHarAvslagService.migrerMeldekort(listOf(23L), 0.milliseconds)

        verify(exactly = 1) {
            personObserverMeldekortregister.sendStoppMeldingTilMeldekortregister(
                person = person,
                fraOgMed = vedtakhendelse.startDato,
                tilOgMed = vedtakhendelse.sluttDato,
                harRett = vedtakhendelse.utfall,
                korrelasjonsId = any(),
            )
        }
    }

    @Test
    @Suppress("ktlint:standard:max-line-length")
    fun `migrerMeldekort kaller personObserverMeldekortregister med den eldste Vedtakhendelsen hvis bruker har flere VedtakHendelser med utfall = false, og mottat i tidsrommet feilen oppsto`() {
        val nyesteVedtakhendelse =
            lagVedtakhendelse(dato = tidspunktFeilenBleFikset.minusMinutes(1), utfall = false)
        val eldsteVedtakhendelse =
            lagVedtakhendelse(dato = tidspunktFeilenOppsto.plusMinutes(1), utfall = false)
        val person =
            Person("12345678901").apply {
                this.hendelser.addAll(listOf(nyesteVedtakhendelse, eldsteVedtakhendelse))
            }
        every { personRepository.hentPerson(23L) } returns person

        manuellMigreringAvMeldekortForBrukereSomBareHarAvslagService.migrerMeldekort(listOf(23L), 0.milliseconds)

        verify(exactly = 1) {
            personObserverMeldekortregister.sendStoppMeldingTilMeldekortregister(
                person = person,
                fraOgMed = eldsteVedtakhendelse.startDato,
                tilOgMed = eldsteVedtakhendelse.sluttDato,
                harRett = eldsteVedtakhendelse.utfall,
                korrelasjonsId = any(),
            )
        }
    }

    @Test
    @Suppress("ktlint:standard:max-line-length")
    fun `migrerMeldekort kaller personObserverMeldekortregister hvis bruker har VedtakHendelse med utfall = false og andre hendelser mottat i tidsrommet feilen oppsto`() {
        val vedtakhendelse =
            lagVedtakhendelse(dato = tidspunktFeilenBleFikset.minusMinutes(1), utfall = false)
        val person =
            Person("12345678901").apply {
                this.hendelser.addAll(
                    listOf(vedtakhendelse, lagEnAnnenTypeHendelse(tidspunktFeilenOppsto.plusMinutes(1))),
                )
            }
        every { personRepository.hentPerson(23L) } returns person

        manuellMigreringAvMeldekortForBrukereSomBareHarAvslagService.migrerMeldekort(listOf(23L), 0.milliseconds)

        verify(exactly = 1) {
            personObserverMeldekortregister.sendStoppMeldingTilMeldekortregister(
                person = person,
                fraOgMed = vedtakhendelse.startDato,
                tilOgMed = vedtakhendelse.sluttDato,
                harRett = vedtakhendelse.utfall,
                korrelasjonsId = any(),
            )
        }
    }

    @Test
    @Suppress("ktlint:standard:max-line-length")
    fun `migrerMeldekort kaller personObserverMeldekortregister hvis bruker har VedtakHendelse med utfall = false mottat i tidsrommet feilen oppsto og andre hendelser utenfor tidsrommet`() {
        val vedtakhendelse =
            lagVedtakhendelse(dato = tidspunktFeilenBleFikset.minusMinutes(1), utfall = false)
        val person =
            Person("12345678901").apply {
                this.hendelser.addAll(
                    listOf(vedtakhendelse, lagEnAnnenTypeHendelse(tidspunktFeilenOppsto.minusMinutes(1))),
                )
            }
        every { personRepository.hentPerson(23L) } returns person

        manuellMigreringAvMeldekortForBrukereSomBareHarAvslagService.migrerMeldekort(listOf(23L), 0.milliseconds)

        verify(exactly = 1) {
            personObserverMeldekortregister.sendStoppMeldingTilMeldekortregister(
                person = person,
                fraOgMed = vedtakhendelse.startDato,
                tilOgMed = vedtakhendelse.sluttDato,
                harRett = vedtakhendelse.utfall,
                korrelasjonsId = any(),
            )
        }
    }

    @Test
    @Suppress("ktlint:standard:max-line-length")
    fun `migrerMeldekort kaller personObserverMeldekortregister bare en gang selv om bruker som har VedtakHendelse med utfall = false i tidsrommet feilen oppsto forekommer flere ganmger i listen`() {
        val vedtakhendelse =
            lagVedtakhendelse(dato = tidspunktFeilenBleFikset.minusMinutes(1), utfall = false)
        val person = Person("12345678901").apply { this.hendelser.add(vedtakhendelse) }
        every { personRepository.hentPerson(23L) } returns person

        manuellMigreringAvMeldekortForBrukereSomBareHarAvslagService.migrerMeldekort(listOf(23L, 23L), 0.milliseconds)

        verify(exactly = 1) {
            personObserverMeldekortregister.sendStoppMeldingTilMeldekortregister(
                person = person,
                fraOgMed = vedtakhendelse.startDato,
                tilOgMed = vedtakhendelse.sluttDato,
                harRett = vedtakhendelse.utfall,
                korrelasjonsId = any(),
            )
        }
    }

    @Test
    @Suppress("ktlint:standard:max-line-length")
    fun `migrerMeldekort kaller personObserverMeldekortregister bare en gang per bruker i listen som har VedtakHendelse med utfall = false i tidsrommet feilen`() {
        val vedtakhendelse1 =
            lagVedtakhendelse(dato = tidspunktFeilenBleFikset.minusMinutes(1), utfall = false)
        val vedtakhendelse2 =
            lagVedtakhendelse(dato = tidspunktFeilenOppsto.plusMinutes(1), utfall = false)
        val person1 = Person("12345678901").apply { this.hendelser.add(vedtakhendelse1) }
        val person2 = Person("12345678901").apply { this.hendelser.add(vedtakhendelse2) }
        every { personRepository.hentPerson(1L) } returns person1
        every { personRepository.hentPerson(2L) } returns person2

        manuellMigreringAvMeldekortForBrukereSomBareHarAvslagService.migrerMeldekort(listOf(1L, 2L), 0.milliseconds)

        verify(exactly = 1) {
            personObserverMeldekortregister.sendStoppMeldingTilMeldekortregister(
                person = person1,
                fraOgMed = vedtakhendelse1.startDato,
                tilOgMed = vedtakhendelse1.sluttDato,
                harRett = vedtakhendelse1.utfall,
                korrelasjonsId = any(),
            )
        }
        verify(exactly = 1) {
            personObserverMeldekortregister.sendStoppMeldingTilMeldekortregister(
                person = person2,
                fraOgMed = vedtakhendelse2.startDato,
                tilOgMed = vedtakhendelse2.sluttDato,
                harRett = vedtakhendelse2.utfall,
                korrelasjonsId = any(),
            )
        }
    }

    private fun lagVedtakhendelse(
        dato: LocalDateTime,
        utfall: Boolean,
    ): VedtakHendelse =
        VedtakHendelse(
            newUuid(),
            "12345678901",
            dato = dato,
            startDato = now(),
            referanseId = newUuid().toString(),
            utfall = utfall,
            behandlingskjedeId = newUuid().toString(),
        )

    private fun lagEnAnnenTypeHendelse(dato: LocalDateTime): SøknadHendelse =
        SøknadHendelse(
            newUuid(),
            "12345678901",
            dato = dato,
            startDato = now(),
            referanseId = newUuid().toString(),
        )

    @Test
    @Disabled
    fun `en NPE runtime`() {
        listOf<Child>().maxByOrNull { it.value == "" } as Parent
    }

    open class Parent {
        val value: String = ""
    }

    class Child : Parent()
}
