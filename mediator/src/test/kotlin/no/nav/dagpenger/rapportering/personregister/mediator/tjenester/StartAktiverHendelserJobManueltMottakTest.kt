package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.mockk.mockk
import io.mockk.verify
import no.nav.dagpenger.rapportering.personregister.mediator.jobs.AktiverHendelserJob
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class StartAktiverHendelserJobManueltMottakTest {
    private val testRapid = TestRapid()
    private val aktiverHendelserJob = mockk<AktiverHendelserJob>(relaxed = true)

    init {
        StartAktiverHendelserJobManueltMottak(
            rapidsConnection = testRapid,
            aktiverHendelserJob = aktiverHendelserJob,
        )
    }

    @BeforeEach
    fun setUp() {
        testRapid.reset()
    }

    @Test
    fun `onPacket kaller AktiverHendelserJob sin execute`() {
        testRapid.sendTestMessage("{\"@event_name\":\"ramp_start_aktiver_hendelser_job_manuelt\"}")

        verify(exactly = 1) { aktiverHendelserJob.execute() }
    }
}
