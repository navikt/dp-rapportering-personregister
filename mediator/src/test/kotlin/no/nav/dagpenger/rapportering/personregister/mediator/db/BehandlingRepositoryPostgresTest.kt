package no.nav.dagpenger.rapportering.personregister.mediator.db

import io.kotest.matchers.shouldBe
import no.nav.dagpenger.rapportering.personregister.mediator.db.Postgres.dataSource
import no.nav.dagpenger.rapportering.personregister.mediator.db.Postgres.withMigratedDb
import no.nav.dagpenger.rapportering.personregister.mediator.utils.UUIDv7
import org.junit.jupiter.api.Test

class BehandlingRepositoryPostgresTest {
    private val behandlingRepository = BehandlingRepositoryPostgres(dataSource)

    @Test
    fun `kan lagre behandling og hente siste sakId`() =
        withMigratedDb {
            val ident1 = "12345678901"
            val ident2 = "12345678902"

            val behandlingId11 = UUIDv7.newUuid().toString()
            val behandlingId12 = UUIDv7.newUuid().toString()
            val behandlingId21 = UUIDv7.newUuid().toString()

            val søknadId11 = UUIDv7.newUuid().toString()
            val søknadId12 = UUIDv7.newUuid().toString()
            val søknadId21 = UUIDv7.newUuid().toString()

            val sakId11 = UUIDv7.newUuid().toString()
            val sakId12 = UUIDv7.newUuid().toString()
            val sakId21 = UUIDv7.newUuid().toString()

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
