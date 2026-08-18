package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.mockk.mockk
import io.mockk.verify
import no.nav.dagpenger.rapportering.personregister.mediator.db.MeldingerRepository
import no.nav.dagpenger.rapportering.personregister.mediator.jobs.AktiverHendelserJob
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class StartAktiverHendelserJobManueltMottakTest {
    private val testRapid = TestRapid()
    private val aktiverHendelserJob = mockk<AktiverHendelserJob>(relaxed = true)
    private val meldingerRepository = mockk<MeldingerRepository>(relaxed = true)

    init {
        StartAktiverHendelserJobManueltMottak(
            rapidsConnection = testRapid,
            aktiverHendelserJob = aktiverHendelserJob,
            meldingerRepository = meldingerRepository,
        )
    }

    @BeforeEach
    fun setUp() {
        testRapid.reset()
    }

    @Test
    fun `onPacket kaller AktiverHendelserJob sin execute`() {
        val melding =
            """
            {
                "@event_name": "ramp_start_aktiver_hendelser_job_manuelt"
            }
            """.trimIndent()
        testRapid.sendTestMessage(melding)

        verify(exactly = 1) { aktiverHendelserJob.execute() }
        verify(exactly = 1) { meldingerRepository.lagreInnkommendeMelding(any(), any(), melding) }
    }
}
