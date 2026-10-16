package no.nav.dagpenger.rapportering.personregister.modell.hendelser

import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.mockk.verify
import no.nav.dagpenger.rapportering.personregister.modell.AnsvarligSystem
import no.nav.dagpenger.rapportering.personregister.modell.Arbeidssøkerperiode
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.PersonObserver
import no.nav.dagpenger.rapportering.personregister.modell.Status
import no.nav.dagpenger.rapportering.personregister.modell.TemporalCollection
import no.nav.dagpenger.rapportering.personregister.modell.helper.ident
import no.nav.dagpenger.rapportering.personregister.modell.helper.periodeId
import no.nav.dagpenger.rapportering.personregister.modell.helper.testPerson
import no.nav.dagpenger.rapportering.personregister.modell.helper.vedtakHendelse
import no.nav.dagpenger.rapportering.personregister.modell.helper.vedtakHendelseMedFremtidigStans
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class VedtakHendelseTest {
    private val nå = LocalDate.now().atStartOfDay()
    private val fortid = nå.minusDays(1)
    private val fremtid = nå.plusDays(1)

    @Test
    fun `hendelse med utfall true legges til person`() =
        testPerson {
            behandle(
                vedtakHendelse(
                    utfall = true,
                    startDato = nå,
                    sluttDato = null,
                ),
            )

            hendelser.size shouldBe 1
            (hendelser.first() as VedtakHendelse).utfall shouldBe true
        }

    @Test
    fun `hendelse med utfall true og uten sluttDato setter harRettTilDp til true`() =
        testPerson {
            setHarRettTilDp(false)
            behandle(vedtakHendelse(utfall = true, startDato = nå, sluttDato = null))

            harRettTilDp shouldBe true
        }

    @Test
    fun `hendelse med utfall true setter ansvarligSystem til DP`() =
        testPerson {
            setAnsvarligSystem(AnsvarligSystem.ARENA)
            behandle(
                vedtakHendelse(
                    utfall = true,
                    startDato = nå,
                    sluttDato = null,
                ),
            )

            ansvarligSystem shouldBe AnsvarligSystem.DP
        }

    @Test
    fun `hendelse med utfall false setter harRettTilDp til false`() =
        testPerson {
            setHarRettTilDp(true)
            behandle(
                vedtakHendelse(
                    utfall = false,
                    startDato = nå,
                    sluttDato = null,
                ),
            )

            harRettTilDp shouldBe false
        }

    @Test
    fun `hendelse med utfall true og sluttDato i fortid setter harRettTilDp til false`() =
        testPerson {
            behandle(vedtakHendelse(utfall = true, startDato = fortid, sluttDato = fortid))

            harRettTilDp shouldBe false
        }

    @Test
    fun `hendelse med utfall true sender startmelding`() {
        val korrelasjonsId = UUID.randomUUID()
        val observer = mockk<PersonObserver>(relaxed = true)

        testPerson {
            addObserver(observer)
            setAnsvarligSystem(AnsvarligSystem.DP)
            behandle(vedtakHendelse(utfall = true, startDato = nå, sluttDato = null, korrelasjonsId = korrelasjonsId))

            verify(exactly = 1) {
                observer.sendStartMeldingTilMeldekortregister(any(), any(), any(), korrelasjonsId)
            }
        }
    }

    @Test
    fun `hendelse med utfall true og hvor hendelsen ikke gjelder fremtidig stans sender startmelding`() {
        val korrelasjonsId = UUID.randomUUID()
        val observer = mockk<PersonObserver>(relaxed = true)

        testPerson {
            addObserver(observer)
            behandle(vedtakHendelse(utfall = true, startDato = nå, sluttDato = null, korrelasjonsId = korrelasjonsId))
            verify(exactly = 1) { observer.sendStartMeldingTilMeldekortregister(any(), any(), any(), korrelasjonsId) }
        }
    }

    @Test
    fun `hendelse med utfall false sender ikke startmelding`() {
        val observer = mockk<PersonObserver>(relaxed = true)
        testPerson {
            addObserver(observer)
            behandle(vedtakHendelse(utfall = false, startDato = nå, sluttDato = null))

            verify(exactly = 0) { observer.sendStartMeldingTilMeldekortregister(any(), any(), any(), any()) }
        }
    }

    @Test
    fun `hendelse som gjelder fremtidig stans sender ikke startmelding når sluttDato er i fremtid og utfall er true`() {
        val observer = mockk<PersonObserver>(relaxed = true)
        testPerson {
            addObserver(observer)
            behandle(vedtakHendelseMedFremtidigStans(utfall = true, startDato = nå, sluttDato = fremtid))

            verify(exactly = 0) { observer.sendStartMeldingTilMeldekortregister(any(), any(), any(), any()) }
        }
    }

    @Test
    fun `hendelse med utfall true og sluttDato i fortid sender startmelding og stoppmelding når hendelsen ikke gjelder fremtidig stans`() {
        val korrelasjonsId = UUID.randomUUID()
        val observer = mockk<PersonObserver>(relaxed = true)

        testPerson {
            addObserver(observer)
            behandle(vedtakHendelse(utfall = true, startDato = fortid, sluttDato = fortid, korrelasjonsId = korrelasjonsId))

            verify(exactly = 1) { observer.sendStartMeldingTilMeldekortregister(any(), any(), any(), korrelasjonsId) }
            verify(exactly = 1) {
                observer.sendStoppMeldingTilMeldekortregister(
                    person = any(),
                    fraOgMed = fortid,
                    tilOgMed = fortid,
                    harRett = true,
                    korrelasjonsId = korrelasjonsId,
                )
            }
        }
    }

    @Test
    fun `hendelse med utfall true og sluttDato i dag sender kun startmelding når hendelsen ikke gjelder fremtidig stans`() {
        val korrelasjonsId = UUID.randomUUID()
        val observer = mockk<PersonObserver>(relaxed = true)

        testPerson {
            addObserver(observer)
            behandle(vedtakHendelse(utfall = true, startDato = nå, sluttDato = nå, korrelasjonsId = korrelasjonsId))

            verify(exactly = 1) { observer.sendStartMeldingTilMeldekortregister(any(), any(), any(), korrelasjonsId) }
            verify(exactly = 0) { observer.sendStoppMeldingTilMeldekortregister(any(), any(), any(), any(), any()) }
        }
    }

    @Test
    fun `hendelse med utfall true og sluttDato i dag sender ingen melding når hendelsen gjelder fremtidig stans`() {
        val observer = mockk<PersonObserver>(relaxed = true)
        testPerson {
            addObserver(observer)
            behandle(vedtakHendelseMedFremtidigStans(utfall = true, startDato = nå, sluttDato = nå))

            verify(exactly = 0) { observer.sendStartMeldingTilMeldekortregister(any(), any(), any(), any()) }
            verify(exactly = 0) { observer.sendStoppMeldingTilMeldekortregister(any(), any(), any(), any(), any()) }
        }
    }

    @Test
    fun `hendelse med utfall true og sluttDato i fremtid sender kun startmelding, ikke stoppmelding`() {
        val korrelasjonsId = UUID.randomUUID()
        val observer = mockk<PersonObserver>(relaxed = true)

        testPerson {
            addObserver(observer)
            behandle(vedtakHendelse(utfall = true, startDato = nå, sluttDato = fremtid, korrelasjonsId = korrelasjonsId))

            verify(exactly = 1) { observer.sendStartMeldingTilMeldekortregister(any(), any(), any(), korrelasjonsId) }
            verify(exactly = 0) { observer.sendStoppMeldingTilMeldekortregister(any(), any(), any(), any(), any()) }
        }
    }

    @Test
    fun `hendelse med utfall true og ingen sluttDato sender kun startmelding, ikke stoppmelding`() {
        val korrelasjonsId = UUID.randomUUID()
        val observer = mockk<PersonObserver>(relaxed = true)

        testPerson {
            addObserver(observer)
            behandle(vedtakHendelse(utfall = true, startDato = nå, sluttDato = null, korrelasjonsId = korrelasjonsId))

            verify(exactly = 1) { observer.sendStartMeldingTilMeldekortregister(any(), any(), any(), korrelasjonsId) }
            verify(exactly = 0) { observer.sendStoppMeldingTilMeldekortregister(any(), any(), any(), any(), any()) }
        }
    }

    @Test
    fun `hendelse med utfall false sender stoppmelding`() {
        val korrelasjonsId = UUID.randomUUID()
        val observer = mockk<PersonObserver>(relaxed = true)

        testPerson {
            addObserver(observer)
            setAnsvarligSystem(AnsvarligSystem.DP)
            behandle(vedtakHendelse(utfall = false, startDato = nå, sluttDato = null, korrelasjonsId = korrelasjonsId))

            verify(exactly = 1) {
                observer.sendStoppMeldingTilMeldekortregister(
                    person = any(),
                    fraOgMed = nå,
                    tilOgMed = null,
                    harRett = false,
                    korrelasjonsId = korrelasjonsId,
                )
            }
        }
    }

    @Test
    fun `erFremtidigStansHendelse returnerer true for hendelse laget med medFremtidigStopp`() {
        val hendelse = vedtakHendelseMedFremtidigStans(utfall = true, startDato = nå, sluttDato = fremtid)

        hendelse.erFremtidigStansHendelse() shouldBe true
    }

    @Test
    fun `erFremtidigStansHendelse returnerer false for vanlig VedtakHendelse`() {
        val hendelse = vedtakHendelse(utfall = true, startDato = nå, sluttDato = null)

        hendelse.erFremtidigStansHendelse() shouldBe false
    }

    @Test
    fun `skal endre status til DAGPENGERBRUKER og sende overtakelsesmelding når prosesserer vedtak med innvilgelse`() {
        val korrelasjonsId = UUID.randomUUID()
        val observer = mockk<PersonObserver>(relaxed = true)
        val statusHistorikk = TemporalCollection<Status>()
        statusHistorikk.put(LocalDateTime.now(), Status.IKKE_DAGPENGERBRUKER)

        val person =
            Person(
                ident,
                statusHistorikk = statusHistorikk,
                arbeidssøkerperioder =
                    mutableListOf(
                        Arbeidssøkerperiode(
                            periodeId,
                            ident,
                            LocalDateTime.now(),
                            null,
                            overtattBekreftelse = false,
                        ),
                    ),
            )

        person.status shouldBe Status.IKKE_DAGPENGERBRUKER

        person.addObserver(observer)
        person.behandle(
            vedtakHendelse(
                utfall = true,
                startDato = nå,
                sluttDato = null,
                korrelasjonsId = korrelasjonsId,
            ),
        )

        person.status shouldBe Status.DAGPENGERBRUKER
        verify(exactly = 1) { observer.sendOvertakelsesmelding(person, korrelasjonsId) }
    }

    @Test
    fun `skal endre status til IKKE_DAGPENGERBRUKER og sende frasigelsesmelding når prosesserer vedtak med avslag`() {
        val korrelasjonsId = UUID.randomUUID()
        val observer = mockk<PersonObserver>(relaxed = true)
        val statusHistorikk = TemporalCollection<Status>()
        statusHistorikk.put(LocalDateTime.now(), Status.DAGPENGERBRUKER)

        val person =
            Person(
                ident,
                statusHistorikk = statusHistorikk,
                arbeidssøkerperioder =
                    mutableListOf(
                        Arbeidssøkerperiode(
                            periodeId,
                            ident,
                            LocalDateTime.now(),
                            null,
                            overtattBekreftelse = true,
                        ),
                    ),
            )

        person.status shouldBe Status.DAGPENGERBRUKER

        person.addObserver(observer)
        person.behandle(
            vedtakHendelse(
                utfall = false,
                startDato = nå,
                sluttDato = null,
                korrelasjonsId = korrelasjonsId,
            ),
        )

        person.status shouldBe Status.IKKE_DAGPENGERBRUKER
        verify(exactly = 1) { observer.sendFrasigelsesmelding(person, false, korrelasjonsId) }
    }

    @Test
    fun `skal endre status til IKKE_DAGPENGERBRUKER men skal ikke sende frasigelsesmelding uten gjeldende arbeidssøkerperioder`() {
        val korrelasjonsId = UUID.randomUUID()
        val observer = mockk<PersonObserver>(relaxed = true)
        val statusHistorikk = TemporalCollection<Status>()
        statusHistorikk.put(LocalDateTime.now(), Status.DAGPENGERBRUKER)

        val person =
            Person(
                ident,
                statusHistorikk = statusHistorikk,
            )

        person.status shouldBe Status.DAGPENGERBRUKER

        person.addObserver(observer)
        person.behandle(
            vedtakHendelse(
                utfall = false,
                startDato = nå,
                sluttDato = null,
                korrelasjonsId = korrelasjonsId,
            ),
        )

        person.status shouldBe Status.IKKE_DAGPENGERBRUKER
        verify(exactly = 0) { observer.sendFrasigelsesmelding(any(), any(), any()) }
    }
}
