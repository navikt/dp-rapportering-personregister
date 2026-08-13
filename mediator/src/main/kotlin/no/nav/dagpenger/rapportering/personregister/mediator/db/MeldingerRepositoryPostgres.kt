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
    ): Int =
        sessionOf(dataSource).use { session ->
            var antallRaderOpprettet = 0
            try {
                antallRaderOpprettet =
                    session.run(
                        queryOf(
                            "INSERT INTO meldinger_innkommende (korrelasjons_id, ident, relevant_meldingsinnhold, tidspunkt) VALUES (?, ?, ?::jsonb, CURRENT_TIMESTAMP)",
                            korrelasjonsId,
                            ident,
                            relevantMeldingsinnhold,
                        ).asUpdate,
                    )

                if (antallRaderOpprettet == 0) {
                    throw RuntimeException("Ingen opprettede rader")
                }
            } catch (e: Exception) {
                logger.error(e) { "Feil ved lagring av innkommende melding" }
            }
            antallRaderOpprettet
        }

    override fun lagreUtgåendeMelding(
        korrelasjonsId: UUID,
        ident: String,
        melding: String,
    ): Int =
        sessionOf(dataSource).use { session ->
            var antallRaderOpprettet = 0
            try {
                antallRaderOpprettet =
                    session.run(
                        queryOf(
                            "INSERT INTO meldinger_utgående (korrelasjons_id, ident, melding, tidspunkt) VALUES (?, ?, ?::jsonb, CURRENT_TIMESTAMP)",
                            korrelasjonsId,
                            ident,
                            melding,
                        ).asUpdate,
                    )

                if (antallRaderOpprettet == 0) {
                    throw RuntimeException("Ingen opprettede rader")
                }
            } catch (e: Exception) {
                logger.error(e) { "Feil ved lagring av utgående melding" }
            }
            antallRaderOpprettet
        }
}
