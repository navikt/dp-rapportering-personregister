package no.nav.dagpenger.rapportering.personregister.mediator.db

import kotliquery.Row
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.ActionTimer
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.ArbeidssøkerBeslutning
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.Handling
import javax.sql.DataSource

class PostgressArbeidssøkerBeslutningRepository(
    private val dataSource: DataSource,
    private val actionTimer: ActionTimer,
) : ArbeidssøkerBeslutningRepository {
    override fun hentBeslutning(id: String) =
        actionTimer.timedAction("db-hentBeslutning") {
            sessionOf(dataSource).use { session ->
                val personId = hentPersonId(id)

                session.run(
                    queryOf(
                        """
                        SELECT periode_id, handling, begrunnelse, opprettet
                        FROM arbeidssoker_beslutning
                        WHERE person_id = ?
                        """,
                        personId,
                    ).map { tilArbeidssøkerBeslutning(it, id) }.asSingle,
                )
            }
        }

    override fun lagreBeslutning(beslutning: ArbeidssøkerBeslutning) {
        actionTimer.timedAction("db-lagreBeslutning") {
            sessionOf(dataSource).use { session ->
                val personId = hentPersonId(beslutning.ident)

                session.transaction { tx ->
                    tx.run(
                        queryOf(
                            """
                        INSERT INTO arbeidssoker_beslutning (person_id, periode_id, handling, begrunnelse, opprettet)
                        VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
                        """,
                            personId,
                            beslutning.periodeId,
                            beslutning.handling.toString(),
                            beslutning.begrunnelse,
                        ).asUpdate,
                    )
                }
            }
        }
    }

    override fun hentBeslutninger(ident: String): List<ArbeidssøkerBeslutning> =
        actionTimer.timedAction("db-hentBeslutninger") {
            sessionOf(dataSource).use { session ->
                val personId = hentPersonId(ident)

                session.run(
                    queryOf(
                        """
                        SELECT periode_id, handling, begrunnelse, opprettet
                        FROM arbeidssoker_beslutning
                        WHERE person_id = ?
                        """,
                        personId,
                    ).map { tilArbeidssøkerBeslutning(it, ident) }.asList,
                )
            }
        }

    private fun hentPersonId(ident: String): Long =
        sessionOf(dataSource).use { session ->
            session.run(
                queryOf("SELECT id FROM person WHERE ident = ?", ident)
                    .map { it.long("id") }
                    .asSingle,
            ) ?: throw IllegalStateException("Person with ident $ident not found")
        }

    private fun tilArbeidssøkerBeslutning(
        row: Row,
        ident: String,
    ): ArbeidssøkerBeslutning =
        ArbeidssøkerBeslutning(
            ident = ident,
            periodeId = row.uuid("periode_id"),
            handling = Handling.valueOf(row.string("handling")),
            begrunnelse = row.string("begrunnelse"),
        )
}
