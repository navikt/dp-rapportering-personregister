package no.nav.dagpenger.rapportering.personregister.mediator.db

import kotliquery.queryOf
import kotliquery.sessionOf
import java.util.UUID
import javax.sql.DataSource

class MeldingerRepositoryPostgres(
    private val dataSource: DataSource,
) : MeldingerRepository {
    override fun lagreInnkommendeMelding(
        korrelasjonsId: UUID,
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
        }.let { if (it == 0) throw Exception("Lagring av innkommende melding feilet") }

    override fun lagreUtgåendeMelding(
        korrelasjonsId: UUID,
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
        }.let { if (it == 0) throw Exception("Lagring av utgående melding feilet") }
}
