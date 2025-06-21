package no.nav.dagpenger.rapportering.personregister.modell.hendelser

import io.kotest.matchers.shouldBe
import no.nav.dagpenger.rapportering.personregister.modell.Status
import no.nav.dagpenger.rapportering.personregister.modell.helper.annenMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.helper.arbeidssøker
import no.nav.dagpenger.rapportering.personregister.modell.helper.dagpengerMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.helper.testPerson
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class DagpengerMeldegruppeHendelseTest {
    private val nå = LocalDateTime.now()
    private val tidligere = nå.minusDays(1)

    @Test
    fun `Ignorer hendelsen der den gjelder tilbake i tid`() =
        testPerson {
            behandle(annenMeldegruppeHendelse(startDato = nå))
            behandle(dagpengerMeldegruppeHendelse(startDato = tidligere))

            meldegruppe shouldBe "ARBS"
        }

    @Test
    fun `behandler hendelsen`() =
        testPerson {
            behandle(annenMeldegruppeHendelse(startDato = tidligere))
            behandle(dagpengerMeldegruppeHendelse(startDato = nå))

            meldegruppe shouldBe "DAGP"
        }

    @Test
    fun `behandler flere hendelser i riktig rekkefølge`() =
        testPerson {
            behandle(annenMeldegruppeHendelse(startDato = nå.minusDays(2)))
            behandle(dagpengerMeldegruppeHendelse(startDato = nå.minusDays(1)))
            behandle(annenMeldegruppeHendelse(startDato = nå))
            behandle(dagpengerMeldegruppeHendelse(startDato = nå.minusDays(1)))

            meldegruppe shouldBe "ARBS"
        }

    @Test
    fun `ignorerer hendelse med samme startdato som eksisterende`() =
        testPerson {
            behandle(dagpengerMeldegruppeHendelse(startDato = nå))
            behandle(annenMeldegruppeHendelse(startDato = nå))

            meldegruppe shouldBe "DAGP"
        }

    @Test
    fun `oppdaterer status når krav oppfylles`() =
        arbeidssøker {
            setMeldeplikt(true)
            behandle(dagpengerMeldegruppeHendelse(startDato = nå))

            status shouldBe Status.DAGPENGERBRUKER
        }

    @Test
    fun `oppdaterer status når krav ikke oppfylles`() =
        arbeidssøker {
            behandle(annenMeldegruppeHendelse(startDato = nå))

            status shouldBe Status.IKKE_DAGPENGERBRUKER
        }

    @Test
    fun `håndterer tom liste av hendelser`() =
        testPerson {
            meldegruppe shouldBe null
        }
}
