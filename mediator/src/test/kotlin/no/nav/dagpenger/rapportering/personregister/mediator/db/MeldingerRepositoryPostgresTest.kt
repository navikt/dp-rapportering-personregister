package no.nav.dagpenger.rapportering.personregister.mediator.db

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.shouldBe
import no.nav.dagpenger.rapportering.personregister.mediator.db.Postgres.dataSource
import no.nav.dagpenger.rapportering.personregister.mediator.db.Postgres.withMigratedDb
import no.nav.dagpenger.rapportering.personregister.mediator.utils.UUIDv7
import org.junit.jupiter.api.Test

class MeldingerRepositoryPostgresTest {
    private val meldingerRepository = MeldingerRepositoryPostgres(dataSource)

    @Test
    fun `lagreInnkommendeMelding kan lagre innkommende melding`() =
        withMigratedDb {
            val ident = "12345678901"

            val antallOpprettedeRader =
                shouldNotThrowAny {
                    meldingerRepository.lagreInnkommendeMelding(
                        ident = ident,
                        relevantMeldingsinnhold = "{ \"key\": \"value\" }",
                    )
                }

            antallOpprettedeRader shouldBe 1
        }

    @Test
    fun `lagreInnkommendeMelding oppretter ingen rader og kaster ikke exception hvis relevantMeldingsinnhold ikke er gyldig JSON`() =
        withMigratedDb {
            val ident = "12345678901"

            val antallOpprettedeRader =
                shouldNotThrowAny {
                    meldingerRepository.lagreInnkommendeMelding(
                        ident = ident,
                        relevantMeldingsinnhold = "bla bla bla",
                    )
                }

            antallOpprettedeRader shouldBe 0
        }

    @Test
    fun `lagreUtgåendeMelding kan lagre utgående melding`() =
        withMigratedDb {
            val ident = "12345678901"

            val antallOpprettedeRader =
                shouldNotThrowAny {
                    meldingerRepository.lagreUtgåendeMelding(
                        korrelasjonsId = UUIDv7.newUuid(),
                        ident = ident,
                        melding = "{ \"key\": \"value\" }",
                    )
                }

            antallOpprettedeRader shouldBe 1
        }

    @Test
    fun `lagreUtgåendeMelding oppretter ingen rader og kaster ikke exception hvis melding ikke er gyldig JSON`() =
        withMigratedDb {
            val ident = "12345678901"

            val antallOpprettedeRader =
                shouldNotThrowAny {
                    meldingerRepository.lagreUtgåendeMelding(
                        korrelasjonsId = UUIDv7.newUuid(),
                        ident = ident,
                        melding = "bla bla bla",
                    )
                }

            antallOpprettedeRader shouldBe 0
        }
}
