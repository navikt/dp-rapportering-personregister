package no.nav.dagpenger.rapportering.personregister.mediator.db

import io.kotest.matchers.shouldBe
import no.nav.dagpenger.rapportering.personregister.mediator.db.Postgres.dataSource
import no.nav.dagpenger.rapportering.personregister.mediator.db.Postgres.withMigratedDb
import org.junit.jupiter.api.Test
import java.util.UUID

class BehandlingRepositoryPostgresTest {
    private val behandlingRepository = BehandlingRepositoryPostgres(dataSource)

    @Test
    fun `kan lagre behandling og hente siste sakId`() =
        withMigratedDb {
            val ident1 = "12345678901"
            val ident2 = "12345678902"

            val behandlingId11 = UUID.randomUUID().toString()
            val behandlingId12 = UUID.randomUUID().toString()
            val behandlingId21 = UUID.randomUUID().toString()

            val søknadId11 = UUID.randomUUID().toString()
            val søknadId12 = UUID.randomUUID().toString()
            val søknadId21 = UUID.randomUUID().toString()

            val sakId11 = UUID.randomUUID().toString()
            val sakId12 = UUID.randomUUID().toString()
            val sakId21 = UUID.randomUUID().toString()

            behandlingRepository.hentSisteSakId(ident1) shouldBe null
            behandlingRepository.hentSisteSakId(ident2) shouldBe null

            behandlingRepository.lagreData(
                behandlingId11,
                søknadId11,
                ident1,
                sakId11,
            )

            behandlingRepository.hentSisteSakId(ident1) shouldBe sakId11
            behandlingRepository.hentSisteSakId(ident2) shouldBe null

            behandlingRepository.lagreData(
                behandlingId12,
                søknadId12,
                ident1,
                sakId12,
            )

            behandlingRepository.hentSisteSakId(ident1) shouldBe sakId12
            behandlingRepository.hentSisteSakId(ident2) shouldBe null

            behandlingRepository.lagreData(
                behandlingId21,
                søknadId21,
                ident2,
                sakId21,
            )

            behandlingRepository.hentSisteSakId(ident1) shouldBe sakId12
            behandlingRepository.hentSisteSakId(ident2) shouldBe sakId21
        }
}
