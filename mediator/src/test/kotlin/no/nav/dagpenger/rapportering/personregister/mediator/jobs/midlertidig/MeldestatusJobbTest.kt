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
import no.nav.dagpenger.rapportering.personregister.mediator.db.TempPerson
import no.nav.dagpenger.rapportering.personregister.mediator.db.TempPersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.db.TempPersonStatus
import no.nav.dagpenger.rapportering.personregister.mediator.service.PersonService
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.Status
import no.nav.dagpenger.rapportering.personregister.modell.TemporalCollection
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class MeldestatusJobbTest {
    val personRepository = InMemoryPersonRepository()
    val personService = mockk<PersonService>()
    val tempPersonRepository = mockk<TempPersonRepository>(relaxed = true)
    val meldepliktConnector = mockk<MeldepliktConnector>(relaxed = true)
    val meldestatusMediator = mockk<MeldestatusMediator>(relaxed = true)

    @Test
    fun `oppdaterStatus skal oppdatere meldepliktstatus for alle personer`() {
        val ident1 = "0102031231"
        val ident2 = "0102031232"
        val ident3 = "0102031233"
        val ident4 = "0102031234"

        every { tempPersonRepository.hentIdenterMedStatus(eq(TempPersonStatus.IKKE_PABEGYNT)) } returns
            listOf(
                ident1,
                ident2,
                ident3,
                ident4,
            ) andThen emptyList()

        val oppdatertTempPersoner = mutableListOf<TempPerson>()
        every { tempPersonRepository.oppdaterPerson(capture(oppdatertTempPersoner)) } returns null

        coEvery { meldepliktConnector.hentMeldestatus(any(), eq(ident3), any()) } returns null
        coEvery { meldepliktConnector.hentMeldestatus(any(), eq(ident4), any()) } returns null

        val behandletPersoner = mutableListOf<Person>()
        every { meldestatusMediator.behandleHendelse(any(), capture(behandletPersoner), any()) } just runs

        personRepository.lagrePerson(Person(ident1))
        personRepository.lagrePerson(Person(ident2))
        personRepository.lagrePerson(Person(ident3))

        val statusHistorikk = TemporalCollection<Status>()
        statusHistorikk.put(LocalDateTime.now(), Status.DAGPENGERBRUKER)
        personRepository.lagrePerson(Person(ident4, statusHistorikk))

        every { personService.hentPerson(ident1) } returns personRepository.hentPerson(ident1)
        every { personService.hentPerson(ident2) } returns personRepository.hentPerson(ident2)
        every { personService.hentPerson(ident3) } returns personRepository.hentPerson(ident3)
        every { personService.hentPerson(ident4) } returns personRepository.hentPerson(ident4)

        val antallPersoner =
            runBlocking {
                MeldestatusJob().oppdaterStatus(
                    personService,
                    tempPersonRepository,
                    meldepliktConnector,
                    meldestatusMediator,
                )
            }

        antallPersoner shouldBe 4

        coVerify { meldepliktConnector.hentMeldestatus(any(), eq(ident1), any()) }
        coVerify { meldepliktConnector.hentMeldestatus(any(), eq(ident2), any()) }
        coVerify { meldepliktConnector.hentMeldestatus(any(), eq(ident3), any()) }
        coVerify { meldepliktConnector.hentMeldestatus(any(), eq(ident4), any()) }

        // ident1 og ident2 skal settes til FERDIGSTILT (OK-scenario)
        // ident3 får meldesttaus = null, har ikke status DAGPENGERBRUKER og skal settes til FERDIGSTILT
        // ident4 får meldesttaus = null, har status DAGPENGERBRUKER og skal settes til AVVIK
        oppdatertTempPersoner.size shouldBe 4
        oppdatertTempPersoner[0].ident shouldBe ident1
        oppdatertTempPersoner[0].status shouldBe TempPersonStatus.FERDIGSTILT
        oppdatertTempPersoner[1].ident shouldBe ident2
        oppdatertTempPersoner[1].status shouldBe TempPersonStatus.FERDIGSTILT
        oppdatertTempPersoner[2].ident shouldBe ident3
        oppdatertTempPersoner[2].status shouldBe TempPersonStatus.FERDIGSTILT
        oppdatertTempPersoner[3].ident shouldBe ident4
        oppdatertTempPersoner[3].status shouldBe TempPersonStatus.AVVIK

        behandletPersoner.size shouldBe 2
        behandletPersoner[0].ident shouldBe ident1
        behandletPersoner[1].ident shouldBe ident2
    }
}
