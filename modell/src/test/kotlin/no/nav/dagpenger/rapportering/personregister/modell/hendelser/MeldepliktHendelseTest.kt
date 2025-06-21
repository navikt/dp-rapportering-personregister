package no.nav.dagpenger.rapportering.personregister.modell.hendelser

import io.kotest.matchers.shouldBe
import no.nav.dagpenger.rapportering.personregister.modell.Status
import no.nav.dagpenger.rapportering.personregister.modell.helper.arbeidssøker
import no.nav.dagpenger.rapportering.personregister.modell.helper.meldepliktHendelse
import no.nav.dagpenger.rapportering.personregister.modell.helper.testPerson
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class MeldepliktHendelseTest {
    private val nå = LocalDateTime.now()
    private val tidligere = nå.minusDays(1)

    @Test
    fun `Ignorer hendelsen der den gjelder tilbake i tid`() =
        testPerson {
            behandle(meldepliktHendelse(startDato = nå, status = true))
            behandle(meldepliktHendelse(startDato = tidligere, status = false))

            meldeplikt shouldBe true
        }

    @Test
    fun `behandler hendelsen`() =
        testPerson {
            behandle(meldepliktHendelse(startDato = tidligere, status = true))
            behandle(meldepliktHendelse(startDato = nå, status = false))

            meldeplikt shouldBe false
        }

    @Test
    fun `behandler flere hendelser i riktig rekkefølge`() =
        testPerson {
            behandle(meldepliktHendelse(startDato = nå, status = false))
            behandle(meldepliktHendelse(startDato = nå.plusDays(1), status = false))
            behandle(meldepliktHendelse(startDato = nå.plusDays(2), status = true))
            behandle(meldepliktHendelse(startDato = nå.plusDays(1), status = false))

            meldeplikt shouldBe true
        }

    @Test
    fun `ignorerer hendelse med samme startdato som eksisterende`() =
        testPerson {
            behandle(meldepliktHendelse(startDato = nå, status = true))
            behandle(meldepliktHendelse(startDato = nå, status = false))

            meldeplikt shouldBe true
        }

    @Test
    fun `oppdaterer status når krav oppfylles`() =
        arbeidssøker {
            setMeldegruppe("DAGP")
            behandle(meldepliktHendelse(startDato = nå, status = true))

            status shouldBe Status.DAGPENGERBRUKER
        }

    @Test
    fun `oppdaterer status når krav ikke oppfylles`() =
        arbeidssøker {
            behandle(meldepliktHendelse(startDato = nå, status = false))

            status shouldBe Status.IKKE_DAGPENGERBRUKER
        }

    @Test
    fun `håndterer tom liste av hendelser`() =
        testPerson {
            meldegruppe shouldBe null
        }
}
