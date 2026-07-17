package no.nav.dagpenger.rapportering.personregister.mediator.db

import no.nav.dagpenger.rapportering.personregister.mediator.db.Postgres.dataSource
import no.nav.dagpenger.rapportering.personregister.mediator.db.Postgres.withMigratedDb
import org.junit.jupiter.api.Test

class MeldingerRepositoryPostgresTest {
    private val meldingerRepository = MeldingerRepositoryPostgres(dataSource)

    @Test
    fun `kan lagre innkommende melding`() =
        withMigratedDb {
            val ident = "12345678901"

            meldingerRepository.lagreInnkommendeMelding(
                ident = ident,
                relevantMeldingsinnhold = "{ \"key\": \"value\" }",
            )
        }

    @Test
    fun `kan lagre utgående melding`() =
        withMigratedDb {
            val ident = "12345678901"

            meldingerRepository.lagreUtgåendeMelding(
                ident = ident,
                melding = "{ \"key\": \"value\" }",
            )
        }
}
