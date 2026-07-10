package no.nav.dagpenger.rapportering.personregister.modell.hendelser

import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.mockk.verify
import no.nav.dagpenger.rapportering.personregister.modell.AnsvarligSystem
import no.nav.dagpenger.rapportering.personregister.modell.PersonObserver
import no.nav.dagpenger.rapportering.personregister.modell.helper.testPerson
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class NødbremsHendelseTest {
    @Test
    fun `nødbrems setter ansvarlig system til arena og tar bort rett til dagpenger`() =
        testPerson {
            setAnsvarligSystem(AnsvarligSystem.DP)
            setHarRettTilDp(true)

            behandle(
                NødbremsHendelse(
                    ident = ident,
                    startDato = LocalDateTime.now(),
                    referanseId = "nødbrems-1",
                ),
            )

            ansvarligSystem shouldBe AnsvarligSystem.ARENA
            harRettTilDp shouldBe false
        }

    @Test
    fun `nødbrems sender stoppmelding med harRett false`() {
        val observer = mockk<PersonObserver>(relaxed = true)

        testPerson {
            addObserver(observer)
            behandle(
                NødbremsHendelse(
                    ident = ident,
                    startDato = LocalDateTime.now(),
                    referanseId = "nødbrems-2",
                ),
            )

            verify(exactly = 1) { observer.sendStoppMeldingTilMeldekortregister(any(), any(), any(), false) }
        }
    }
}
