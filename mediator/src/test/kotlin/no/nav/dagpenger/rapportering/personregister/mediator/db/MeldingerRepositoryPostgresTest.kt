package no.nav.dagpenger.rapportering.personregister.mediator.db

import io.kotest.assertions.throwables.shouldNotThrowAny
import no.nav.dagpenger.rapportering.personregister.mediator.db.Postgres.dataSource
import no.nav.dagpenger.rapportering.personregister.mediator.db.Postgres.withMigratedDb
import no.nav.dagpenger.rapportering.personregister.mediator.utils.UUIDv7
import org.junit.jupiter.api.Test

class MeldingerRepositoryPostgresTest {
    private val meldingerRepository = MeldingerRepositoryPostgres(dataSource)

    @Test
    fun `kan lagre innkommende melding`() =
        withMigratedDb {
            val ident = "12345678901"

            shouldNotThrowAny {
                meldingerRepository.lagreInnkommendeMelding(
                    ident = ident,
                    relevantMeldingsinnhold = "{ \"key\": \"value\" }",
                )
            }
        }

    @Test
    fun `kaster ikke exception hvis relevantMeldingsinnhold ikke er gyldig JSON`() =
        withMigratedDb {
            val ident = "12345678901"

            shouldNotThrowAny {
                meldingerRepository.lagreInnkommendeMelding(
                    ident = ident,
                    relevantMeldingsinnhold = "bla bla bla",
                )
            }
        }

    @Test
    fun `kan lagre utgående melding`() =
        withMigratedDb {
            val ident = "12345678901"

            shouldNotThrowAny {
                meldingerRepository.lagreUtgåendeMelding(
                    korrelasjonsId = UUIDv7.newUuid(),
                    ident = ident,
                    melding = "{ \"key\": \"value\" }",
                )
            }
        }

    @Test
    fun `kaster ikke exception hvis melding ikke er gyldig JSON`() =
        withMigratedDb {
            val ident = "12345678901"

            shouldNotThrowAny {
                meldingerRepository.lagreUtgåendeMelding(
                    korrelasjonsId = UUIDv7.newUuid(),
                    ident = ident,
                    melding = "bla bla bla",
                )
            }
        }
}
