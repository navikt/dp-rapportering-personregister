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
import no.nav.dagpenger.rapportering.personregister.mediator.utils.MetrikkerTestUtil.jobbkjøringMetrikker
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
                jobbkjøringMetrikker,
            )

        val taskExecutor =
            TaskExecutor(
                listOf(
                    ScheduledTask(aktiverHendelserJob, 2, 0),
                ),
            )

        val mockedTime = LocalTime.of(1, 59, 58)
        val mockedDateTime = ZonedDateTime.now().with(mockedTime)

        try {
            mockkStatic(ZonedDateTime::class)
            every { ZonedDateTime.of(any(), any()) } returns mockedDateTime andThen mockedDateTime andThen mockedDateTime.minusHours(1)

            taskExecutor.startExecution()

            coVerify(atLeast = 2, timeout = 15000) { personRepository.hentHendelserSomSkalAktiveres() }
        } finally {
            clearStaticMockk(ZonedDateTime::class)
        }
    }
}
