package no.nav.dagpenger.rapportering.personregister.mediator.db

import io.github.oshai.kotlinlogging.KotlinLogging
import kotliquery.queryOf
import kotliquery.sessionOf
import javax.sql.DataSource

class MeldingerRepositoryPostgres(
    private val dataSource: DataSource,
) : MeldingerRepository {
    private val logger = KotlinLogging.logger {}

    override fun lagreInnkommendeMelding(
        korrelasjonsId: String,
        ident: String?,
        relevantMeldingsinnhold: String,
    ) = sessionOf(dataSource)
        .use { session ->
            session
                .run(
                    queryOf(
                        "INSERT INTO meldinger_innkommende " +
                            "(korrelasjonsId, ident, relevant_meldingsinnhold) " +
                            "VALUES (?, ?, ?::jsonb)",
                        korrelasjonsId,
                        ident,
                        relevantMeldingsinnhold,
                    ).asUpdate,
                )
        }.let { if (it == 0) logger.error { "Lagring av innkommende melding feilet" } }

    override fun lagreUtgåendeMelding(
        korrelasjonsId: String,
        ident: String,
        melding: String,
    ) = sessionOf(dataSource)
        .use { session ->
            session
                .run(
                    queryOf(
                        "INSERT INTO meldinger_utgående " +
                            "(korrelasjonsId, ident, melding) " +
                            "VALUES (?, ?, ?::jsonb)",
                        korrelasjonsId,
                        ident,
                        melding,
                    ).asUpdate,
                )
        }.let { if (it == 0) logger.error { "Lagring av utgående melding feilet" } }
}
