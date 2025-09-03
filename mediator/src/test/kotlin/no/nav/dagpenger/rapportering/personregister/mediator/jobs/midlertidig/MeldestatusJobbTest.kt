package no.nav.dagpenger.rapportering.personregister.mediator.jobs.midlertidig

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import no.nav.dagpenger.rapportering.personregister.mediator.MeldestatusMediator
import no.nav.dagpenger.rapportering.personregister.mediator.connector.MeldepliktConnector
import no.nav.dagpenger.rapportering.personregister.mediator.db.InMemoryPersonRepository
import no.nav.dagpenger.rapportering.personregister.modell.Person
import org.junit.jupiter.api.Test

class MeldestatusJobbTest {
    val personRepository = InMemoryPersonRepository()
    val meldepliktConnector = mockk<MeldepliktConnector>(relaxed = true)
    val meldestatusMediator = mockk<MeldestatusMediator>(relaxed = true)

    @Test
    fun `oppdaterStatus skal oppdatere meldepliktstatus for alle personer`() {
        val ident1 = "0102031231"
        val ident2 = "0102031232"
        val ident3 = "0102031233"

        coEvery { meldepliktConnector.hentMeldestatus(any(), eq(ident3), any()) } returns null

        personRepository.lagrePerson(Person(ident1))
        personRepository.lagrePerson(Person(ident2))
        personRepository.lagrePerson(Person(ident3))

        val antallPersoner =
            MeldestatusJob().oppdaterStatus(
                personRepository,
                meldepliktConnector,
                meldestatusMediator,
            )

        antallPersoner shouldBe 3

        coVerify { meldepliktConnector.hentMeldestatus(any(), eq(ident1), any()) }
        coVerify { meldepliktConnector.hentMeldestatus(any(), eq(ident2), any()) }
        coVerify { meldepliktConnector.hentMeldestatus(any(), eq(ident3), any()) }

        coVerify(exactly = 2) { meldestatusMediator.behandleHendelse(any(), any(), any()) }
        coVerify {
            meldestatusMediator.behandleHendelse(
                any(),
                withArg {
                    it.ident shouldBe ident1
                },
                any(),
            )
        }
        coVerify {
            meldestatusMediator.behandleHendelse(
                any(),
                withArg {
                    it.ident shouldBe ident2
                },
                any(),
            )
        }
    }
}
