package no.nav.dagpenger.rapportering.personregister.mediator.db

import io.github.oshai.kotlinlogging.KotlinLogging
import kotliquery.queryOf
import kotliquery.sessionOf
import java.util.UUID
import javax.sql.DataSource

class MeldingerRepositoryPostgres(
    private val dataSource: DataSource,
) : MeldingerRepository {
    private val logger = KotlinLogging.logger {}

    override fun lagreInnkommendeMelding(
        korrelasjonsId: UUID,
        ident: String?,
        relevantMeldingsinnhold: String,
    ) = sessionOf(dataSource)
        .use { session ->
            try {
                val affectedRows =
                    session
                        .run(
                            queryOf(
                                "INSERT INTO meldinger_innkommende " +
                                    "(korrelasjons_id, ident, relevant_meldingsinnhold) " +
                                    "VALUES (?, ?, ?::jsonb)",
                                korrelasjonsId,
                                ident,
                                relevantMeldingsinnhold,
                            ).asUpdate,
                        )

                if (affectedRows == 0) {
                    throw RuntimeException("Ingen berørte rader")
                }
            } catch (e: Exception) {
                logger.error(e) { "Feil ved lagring av innkommende melding" }
            }
        }

    override fun lagreUtgåendeMelding(
        korrelasjonsId: UUID,
        ident: String,
        melding: String,
    ) = sessionOf(dataSource)
        .use { session ->
            try {
                val affectedRows =
                    session
                        .run(
                            queryOf(
                                "INSERT INTO meldinger_utgående " +
                                    "(korrelasjons_id, ident, melding) " +
                                    "VALUES (?, ?, ?::jsonb)",
                                korrelasjonsId,
                                ident,
                                melding,
                            ).asUpdate,
                        )

                if (affectedRows == 0) {
                    throw RuntimeException("Ingen berørte rader")
                }
            } catch (e: Exception) {
                logger.error(e) { "Feil ved lagring av utgående melding" }
            }
        }
}
