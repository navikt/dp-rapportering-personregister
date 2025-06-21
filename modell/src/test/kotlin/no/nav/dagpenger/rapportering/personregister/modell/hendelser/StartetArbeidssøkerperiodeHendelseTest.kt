package no.nav.dagpenger.rapportering.personregister.modell.hendelser

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.dagpenger.rapportering.personregister.modell.Arbeidssøkerperiode
import no.nav.dagpenger.rapportering.personregister.modell.Status.DAGPENGERBRUKER
import no.nav.dagpenger.rapportering.personregister.modell.Status.IKKE_DAGPENGERBRUKER
import no.nav.dagpenger.rapportering.personregister.modell.helper.dagpengerMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.helper.meldepliktHendelse
import no.nav.dagpenger.rapportering.personregister.modell.helper.periodeId
import no.nav.dagpenger.rapportering.personregister.modell.helper.startetArbeidssøkerperiodeHendelse
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
}
