package no.nav.dagpenger.rapportering.personregister.mediator.jobs

import io.ktor.http.HttpStatusCode
import io.mockk.clearStaticMockk
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import no.nav.dagpenger.rapportering.personregister.mediator.MeldestatusMediator
import no.nav.dagpenger.rapportering.personregister.mediator.PersonMediator
import no.nav.dagpenger.rapportering.personregister.mediator.connector.MeldepliktConnector
import no.nav.dagpenger.rapportering.personregister.mediator.connector.createMockClient
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.service.PersonService
import org.junit.jupiter.api.Test
import java.time.LocalTime
import java.time.ZonedDateTime

class TaskExecutorTest {
    @Test
    fun `skal utføre oppgaver`() {
        val personRepository = mockk<PersonRepository>()
        val personService = mockk<PersonService>()
        val personMediator = mockk<PersonMediator>()
        val meldestatusMediator = mockk<MeldestatusMediator>()
        val meldepliktConnector = mockk<MeldepliktConnector>()

        coEvery { personRepository.hentHendelserSomSkalAktiveres() } returns emptyList()

        val aktiverHendelserJob =
            AktiverHendelserJob(
                personRepository,
                personService,
                personMediator,
                meldestatusMediator,
                meldepliktConnector,
                createMockClient(HttpStatusCode.InternalServerError.value, ""),
            )

        val taskExecutor =
            TaskExecutor(
                listOf(
                    ScheduledTask(aktiverHendelserJob, 2, 0),
                ),
            )

        val mockedTime = LocalTime.of(1, 59, 57)
        val mockedDateTime = ZonedDateTime.now().with(mockedTime)
        mockkStatic(ZonedDateTime::class)
        every { ZonedDateTime.of(any(), any()) } returns mockedDateTime

        taskExecutor.startExecution()

        // Venter på taskExecutor
        Thread.sleep(4000)

        // hentHendelserSomSkalAktiveres må kalles én gang etter 4 sekunder
        coVerify(exactly = 1) { personRepository.hentHendelserSomSkalAktiveres() }

        // Venter på taskExecutor
        Thread.sleep(4000)

        // hentHendelserSomSkalAktiveres må kalles én gang til etter 4 sekunder fordi vi har mocked ZonedDateTime
        coVerify(exactly = 2) { personRepository.hentHendelserSomSkalAktiveres() }

        clearStaticMockk(ZonedDateTime::class)
    }
}
