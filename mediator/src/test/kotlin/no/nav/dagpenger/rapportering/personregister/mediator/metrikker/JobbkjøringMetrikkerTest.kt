package no.nav.dagpenger.rapportering.personregister.mediator.metrikker

import io.kotest.matchers.shouldBe
import no.nav.dagpenger.rapportering.personregister.mediator.utils.MetrikkerTestUtil.jobbkjøringMetrikker
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.time.Duration.Companion.seconds

class JobbkjøringMetrikkerTest {
    @Test
    fun `jobbFullfort inkrementerer metrikker`() {
        val jobStatusCount = jobbkjøringMetrikker.jobStatus.count()
        val jobDurationCount = jobbkjøringMetrikker.jobDuration.count()
        val jobDurationTotalTime = jobbkjøringMetrikker.jobDuration.totalTime(SECONDS)
        val jobErrorsCount = jobbkjøringMetrikker.jobErrors.count()
        val affectedRowsCount = jobbkjøringMetrikker.affectedRowsCount.count()

        jobbkjøringMetrikker.jobbFullfort(10.seconds, 2)

        jobbkjøringMetrikker.jobStatus.count() shouldBe jobStatusCount + 1
        jobbkjøringMetrikker.jobDuration.count() shouldBe jobDurationCount + 1
        jobbkjøringMetrikker.jobDuration.totalTime(SECONDS) shouldBe jobDurationTotalTime + 10
        jobbkjøringMetrikker.jobErrors.count() shouldBe jobErrorsCount
        jobbkjøringMetrikker.affectedRowsCount.count() shouldBe affectedRowsCount + 2
    }

    @Test
    fun `jobbFeilet inkrementerer metrikker`() {
        val jobStatusCount = jobbkjøringMetrikker.jobStatus.count()
        val jobDurationCount = jobbkjøringMetrikker.jobDuration.count()
        val jobDurationTotalTime = jobbkjøringMetrikker.jobDuration.totalTime(SECONDS)
        val jobErrorsCount = jobbkjøringMetrikker.jobErrors.count()
        val affectedRowsCount = jobbkjøringMetrikker.affectedRowsCount.count()

        jobbkjøringMetrikker.jobbFeilet()

        jobbkjøringMetrikker.jobStatus.count() shouldBe jobStatusCount
        jobbkjøringMetrikker.jobDuration.count() shouldBe jobDurationCount
        jobbkjøringMetrikker.jobDuration.totalTime(SECONDS) shouldBe jobDurationTotalTime
        jobbkjøringMetrikker.jobErrors.count() shouldBe jobErrorsCount + 1
        jobbkjøringMetrikker.affectedRowsCount.count() shouldBe affectedRowsCount
    }
}
