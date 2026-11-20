package no.nav.dagpenger.rapportering.personregister.mediator.jobs

import io.mockk.clearStaticMockk
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.JobbkjøringMetrikker
import org.junit.jupiter.api.Test
import java.time.LocalTime
import java.time.ZonedDateTime

class TaskExecutorTest {
    @Test
    fun `startExecution kjører scheduled task hver gang konfigurert tidspunkt inntreffer`() {
        try {
            val jobbkjøringMetrikker = mockk<JobbkjøringMetrikker>(relaxed = true)
            val mockedZonedDateTime =
                ZonedDateTime
                    .now()
                    .with(LocalTime.of(1, 59, 59, 999_999_999))
            mockkStatic(ZonedDateTime::class)
            every { ZonedDateTime.now() } returns mockedZonedDateTime
            every { ZonedDateTime.of(any(), any()) } returns mockedZonedDateTime

            TaskExecutor(
                listOf(
                    ScheduledTask(TestTask(jobbkjøringMetrikker), 2, 0),
                ),
            ).startExecution()

            verify(atLeast = 2, timeout = 2500) { jobbkjøringMetrikker.jobbSjekketOmDenSkulleKjøre() }
        } finally {
            clearStaticMockk(ZonedDateTime::class)
        }
    }

    private class TestTask(
        val jobbkjøringMetrikker: JobbkjøringMetrikker,
    ) : Task {
        override fun execute() {
            jobbkjøringMetrikker.jobbSjekketOmDenSkulleKjøre()
        }
    }
}
