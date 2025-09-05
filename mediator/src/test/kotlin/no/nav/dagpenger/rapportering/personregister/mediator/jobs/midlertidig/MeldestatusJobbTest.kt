package no.nav.dagpenger.rapportering.personregister.mediator.jobs.midlertidig

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.rapportering.personregister.mediator.MeldestatusMediator
import no.nav.dagpenger.rapportering.personregister.mediator.connector.MeldepliktConnector
import no.nav.dagpenger.rapportering.personregister.mediator.db.InMemoryPersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.db.TempPersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.db.TempPersonStatus
import no.nav.dagpenger.rapportering.personregister.modell.Person
import org.junit.jupiter.api.Test

class MeldestatusJobbTest {
    val personRepository = InMemoryPersonRepository()
    val tempPersonRepository = mockk<TempPersonRepository>(relaxed = true)
    val meldepliktConnector = mockk<MeldepliktConnector>(relaxed = true)
    val meldestatusMediator = mockk<MeldestatusMediator>(relaxed = true)

    @Test
    fun `oppdaterStatus skal oppdatere meldepliktstatus for alle personer`() {
        val ident1 = "0102031231"
        val ident2 = "0102031232"
        val ident3 = "0102031233"

        every { tempPersonRepository.hentIdenterMedStatus(eq(TempPersonStatus.IKKE_PABEGYNT)) } returns
            listOf(
                ident1,
                ident2,
                ident3,
            ) andThen emptyList()
        coEvery { meldepliktConnector.hentMeldestatus(any(), eq(ident3), any()) } returns null

        val behandletPersoner = mutableListOf<Person>()
        every { meldestatusMediator.behandleHendelse(any(), capture(behandletPersoner), any()) } just runs

        personRepository.lagrePerson(Person(ident1))
        personRepository.lagrePerson(Person(ident2))
        personRepository.lagrePerson(Person(ident3))

        val antallPersoner =
            runBlocking {
                MeldestatusJob().oppdaterStatus(
                    personRepository,
                    tempPersonRepository,
                    meldepliktConnector,
                    meldestatusMediator,
                )
            }

        antallPersoner shouldBe 3

        coVerify { meldepliktConnector.hentMeldestatus(any(), eq(ident1), any()) }
        coVerify { meldepliktConnector.hentMeldestatus(any(), eq(ident2), any()) }
        coVerify { meldepliktConnector.hentMeldestatus(any(), eq(ident3), any()) }

        behandletPersoner.size shouldBe 2
        behandletPersoner[0].ident shouldBe ident1
        behandletPersoner[1].ident shouldBe ident2
    }
}
