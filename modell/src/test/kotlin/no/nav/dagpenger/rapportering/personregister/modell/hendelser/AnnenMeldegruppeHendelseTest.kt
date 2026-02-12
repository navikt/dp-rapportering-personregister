package no.nav.dagpenger.rapportering.personregister.modell.hendelser

import io.kotest.matchers.shouldBe
import no.nav.dagpenger.rapportering.personregister.modell.Status
import no.nav.dagpenger.rapportering.personregister.modell.helper.annenMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.helper.arbeidssøker
import no.nav.dagpenger.rapportering.personregister.modell.helper.dagpengerMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.helper.testPerson
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class AnnenMeldegruppeHendelseTest {
    private val nå = LocalDateTime.now()
    private val tidligere = nå.minusDays(2)

    @Test
    fun `Ignorer hendelsen der den gjelder tilbake i tid`() =
        testPerson {
            behandle(dagpengerMeldegruppeHendelse(startDato = nå))
            behandle(annenMeldegruppeHendelse(startDato = tidligere, sluttDato = nå.minusDays(1)))

            meldegruppe shouldBe "DAGP"
        }

    @Test
    fun `Ikke ignorer hendelse der startDato gjelder tilbake i tid, men sluttDato ikke er satt`() =
        testPerson {
            behandle(dagpengerMeldegruppeHendelse(startDato = nå))
            behandle(annenMeldegruppeHendelse(startDato = tidligere, sluttDato = null))

            meldegruppe shouldBe "ARBS"
        }

    @Test
    fun `behandler hendelsen`() =
        testPerson {
            behandle(dagpengerMeldegruppeHendelse(startDato = tidligere))
            behandle(annenMeldegruppeHendelse(startDato = nå))

            meldegruppe shouldBe "ARBS"
        }

    @Test
    fun `behandler flere hendelser i riktig rekkefølge`() =
        testPerson {
            behandle(dagpengerMeldegruppeHendelse(startDato = nå.minusDays(2)))
            behandle(annenMeldegruppeHendelse(startDato = nå.minusDays(1)))
            behandle(dagpengerMeldegruppeHendelse(startDato = nå))
            behandle(annenMeldegruppeHendelse(startDato = nå.minusDays(1), sluttDato = nå.minusMinutes(1)))

            meldegruppe shouldBe "DAGP"
        }

    @Test
    fun `behandler hendelse med samme startdato som eksisterende`() =
        testPerson {
            behandle(annenMeldegruppeHendelse(startDato = nå))
            behandle(dagpengerMeldegruppeHendelse(startDato = nå))

            meldegruppe shouldBe "DAGP"
        }

    @Test
    fun `oppdaterer status når krav ikke oppfylles`() =
        arbeidssøker {
            behandle(annenMeldegruppeHendelse(startDato = nå))

            status shouldBe Status.IKKE_DAGPENGERBRUKER
        }
}
