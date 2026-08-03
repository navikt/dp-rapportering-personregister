package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import no.nav.dagpenger.rapportering.personregister.mediator.Configuration.defaultObjectMapper
import no.nav.dagpenger.rapportering.personregister.mediator.db.MeldingerRepository
import no.nav.dagpenger.rapportering.personregister.mediator.jobs.AktiverHendelserJob
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class StartAktiverHendelserJobManueltMottakTest {
    private val testRapid = TestRapid()
    private val aktiverHendelserJob = mockk<AktiverHendelserJob>(relaxed = true)
    private val meldingerRepository = mockk<MeldingerRepository>(relaxed = true)

    init {
        System.setProperty("KAFKA_SCHEMA_REGISTRY", "KAFKA_SCHEMA_REGISTRY")
        System.setProperty("KAFKA_SCHEMA_REGISTRY_USER", "KAFKA_SCHEMA_REGISTRY_USER")
        System.setProperty("KAFKA_SCHEMA_REGISTRY_PASSWORD", "KAFKA_SCHEMA_REGISTRY_PASSWORD")
        System.setProperty("KAFKA_BROKERS", "KAFKA_BROKERS")

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
        val melding = "{\"@event_name\":\"ramp_start_aktiver_hendelser_job_manuelt\"}"
        testRapid.sendTestMessage(melding)

        verify(exactly = 1) { aktiverHendelserJob.execute() }
        coVerify(exactly = 1) {
            meldingerRepository.lagreInnkommendeMelding(
                korrelasjonsId = any(),
                ident = null,
                relevantMeldingsinnhold =
                    match { melding ->
                        with(defaultObjectMapper.readTree(melding)) {
                            this["@event_name"].asText() == "ramp_start_aktiver_hendelser_job_manuelt"
                        }
                    },
            )
        }
    }
}
