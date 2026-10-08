package no.nav.dagpenger.rapportering.personregister.modell.hendelser

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.mockk
import io.mockk.verify
import no.nav.dagpenger.rapportering.personregister.modell.AnsvarligSystem
import no.nav.dagpenger.rapportering.personregister.modell.Arbeidssøkerperiode
import no.nav.dagpenger.rapportering.personregister.modell.PersonObserver
import no.nav.dagpenger.rapportering.personregister.modell.Status.DAGPENGERBRUKER
import no.nav.dagpenger.rapportering.personregister.modell.Status.IKKE_DAGPENGERBRUKER
import no.nav.dagpenger.rapportering.personregister.modell.helper.dagpengerMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.helper.meldepliktHendelse
import no.nav.dagpenger.rapportering.personregister.modell.helper.periodeId
import no.nav.dagpenger.rapportering.personregister.modell.helper.startetArbeidssøkerperiodeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.helper.søknadHendelse
import no.nav.dagpenger.rapportering.personregister.modell.helper.testPerson
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class StartetArbeidssøkerperiodeHendelseTest {
    @Test
    fun `skal legge til ny periode når ingen aktiv periode eksisterer`() {
        testPerson {
            val periodeId = UUID.randomUUID()

            behandle(startetArbeidssøkerperiodeHendelse(periodeId = periodeId))

            arbeidssøkerperioder.size shouldBe 1
            arbeidssøkerperioder.first().periodeId shouldBe periodeId
        }
    }

    @Test
    fun `skal avslutte eksisterende aktiv periode før ny legges til`() {
        testPerson {
            arbeidssøkerperioder.add(
                Arbeidssøkerperiode(
                    periodeId = UUID.randomUUID(),
                    ident = ident,
                    startet = LocalDateTime.now().minusDays(10),
                    avsluttet = null,
                    overtattBekreftelse = true,
                ),
            )

            behandle(startetArbeidssøkerperiodeHendelse(periodeId))

            arbeidssøkerperioder.size shouldBe 2
            arbeidssøkerperioder.first().avsluttet shouldNotBe null
            arbeidssøkerperioder.first().overtattBekreftelse shouldBe false
            arbeidssøkerperioder.last().periodeId shouldBe periodeId
            arbeidssøkerperioder.filter { it.avsluttet == null }.size shouldBe 1
        }
    }

    @Test
    fun `skal ikke legge til duplikatperiode`() {
        val periodeId = UUID.randomUUID()

        testPerson {
            arbeidssøkerperioder.add(
                Arbeidssøkerperiode(
                    periodeId = periodeId,
                    ident = "12345",
                    startet = LocalDateTime.now(),
                    avsluttet = null,
                    overtattBekreftelse = false,
                ),
            )

            behandle(startetArbeidssøkerperiodeHendelse(periodeId = periodeId))
            arbeidssøkerperioder.size shouldBe 1
        }
    }

    @Test
    fun `behandler startet arbeidssøker hendelser for bruker som ikke oppfyller kravet`() =
        testPerson {
            behandle(startetArbeidssøkerperiodeHendelse())

            status shouldBe IKKE_DAGPENGERBRUKER
        }

    @Test
    fun `behandler StartetArbeidssøkerperiodeHendelse for bruker som oppfyller kravet`() =
        testPerson {
            behandle(meldepliktHendelse(status = true))
            behandle(dagpengerMeldegruppeHendelse())
            behandle(startetArbeidssøkerperiodeHendelse())

            status shouldBe DAGPENGERBRUKER
        }

    @Suppress("ktlint:standard:max-line-length")
    @Test
    fun `setter harRettTilDp og sender startmelding med nyeste søknadsdato når bruker registreres som arbeidssøker og ansvarlig system er DP`() {
        val korrelasjonsId = UUID.randomUUID()
        val observer = mockk<PersonObserver>(relaxed = true)
        val eldsteSøknadDato = LocalDateTime.now().minusDays(2)
        val nyesteSøknadDato = LocalDateTime.now().minusDays(1)

        testPerson {
            addObserver(observer)
            setAnsvarligSystem(AnsvarligSystem.DP)
            hendelser.add(søknadHendelse(startDato = eldsteSøknadDato, referanseId = "soknad-1"))
            hendelser.add(søknadHendelse(startDato = nyesteSøknadDato, referanseId = "soknad-2"))

            behandle(startetArbeidssøkerperiodeHendelse(korrelasjonsId = korrelasjonsId))

            harRettTilDp shouldBe true
            verify(exactly = 1) {
                observer.sendStartMeldingTilMeldekortregister(
                    person = any(),
                    fraOgMed = nyesteSøknadDato,
                    tilOgMed = null,
                    skalMigreres = false,
                    korrelasjonsId = korrelasjonsId,
                )
            }
        }
    }

    @Test
    fun `sender ikke startmelding og setter ikke harRettTilDp når søknad ikke finnes`() {
        val observer = mockk<PersonObserver>(relaxed = true)

        testPerson {
            addObserver(observer)
            setAnsvarligSystem(AnsvarligSystem.DP)

            behandle(startetArbeidssøkerperiodeHendelse())

            harRettTilDp shouldBe false
            verify(exactly = 0) { observer.sendStartMeldingTilMeldekortregister(any(), any(), any(), any(), any()) }
        }
    }

    @Test
    fun `sender ikke startmelding når harRettTilDp allerede er true`() {
        val observer = mockk<PersonObserver>(relaxed = true)
        val søknadDato = LocalDateTime.now().minusDays(2)

        testPerson {
            addObserver(observer)
            setAnsvarligSystem(AnsvarligSystem.DP)
            setHarRettTilDp(true)
            hendelser.add(søknadHendelse(startDato = søknadDato, referanseId = "soknad-1"))

            behandle(startetArbeidssøkerperiodeHendelse())

            harRettTilDp shouldBe true
            verify(exactly = 0) {
                observer.sendStartMeldingTilMeldekortregister(any(), any(), any(), any(), any())
            }
        }
    }

    @Test
    fun `sender ikke startmelding når det finnes et vedtak som gjelder på eller etter søknadsdatoen`() {
        val korrelasjonsId = UUID.randomUUID()
        val observer = mockk<PersonObserver>(relaxed = true)
        val søknadDato = LocalDateTime.now().minusDays(5)
        val vedtakStartDato = LocalDateTime.now().minusDays(10)
        val vedtakSluttDato = LocalDateTime.now().plusDays(30)

        testPerson {
            addObserver(observer)
            setAnsvarligSystem(AnsvarligSystem.DP)
            hendelser.add(søknadHendelse(startDato = søknadDato, referanseId = "soknad-1"))
            hendelser.add(
                VedtakHendelse(
                    korrelasjonsId = UUID.randomUUID(),
                    ident = ident,
                    dato = LocalDateTime.now().minusDays(20),
                    startDato = vedtakStartDato,
                    referanseId = "vedtak-1",
                    sluttDato = vedtakSluttDato,
                    utfall = false,
                    behandlingskjedeId = null,
                ),
            )

            behandle(startetArbeidssøkerperiodeHendelse(korrelasjonsId = korrelasjonsId))

            harRettTilDp shouldBe false
            verify(exactly = 0) {
                observer.sendStartMeldingTilMeldekortregister(any(), any(), any(), any(), any())
            }
        }
    }

    @Test
    fun `sender startmelding når eksisterende vedtak er før søknadsdatoen`() {
        val korrelasjonsId = UUID.randomUUID()
        val observer = mockk<PersonObserver>(relaxed = true)
        val søknadDato = LocalDateTime.now().minusDays(5)
        val vedtakStartDato = LocalDateTime.now().minusDays(20)
        val vedtakSluttDato = LocalDateTime.now().minusDays(6)

        testPerson {
            addObserver(observer)
            setAnsvarligSystem(AnsvarligSystem.DP)
            hendelser.add(søknadHendelse(startDato = søknadDato, referanseId = "soknad-1"))
            hendelser.add(
                VedtakHendelse(
                    korrelasjonsId = UUID.randomUUID(),
                    ident = ident,
                    dato = LocalDateTime.now().minusDays(25),
                    startDato = vedtakStartDato,
                    referanseId = "vedtak-1",
                    sluttDato = vedtakSluttDato,
                    utfall = false,
                    behandlingskjedeId = null,
                ),
            )

            behandle(startetArbeidssøkerperiodeHendelse(korrelasjonsId = korrelasjonsId))

            harRettTilDp shouldBe true
            verify(exactly = 1) {
                observer.sendStartMeldingTilMeldekortregister(
                    person = any(),
                    fraOgMed = søknadDato,
                    tilOgMed = null,
                    skalMigreres = false,
                    korrelasjonsId = korrelasjonsId,
                )
            }
        }
    }
}
