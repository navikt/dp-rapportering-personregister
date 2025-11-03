package no.nav.dagpenger.rapportering.personregister.mediator.db

import io.github.oshai.kotlinlogging.KotlinLogging
import kotliquery.Row
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import javax.sql.DataSource

private val logger = KotlinLogging.logger {}

class PostgresTempPersonRepository(
    private val dataSource: DataSource,
) : TempPersonRepository {
    override fun hentPerson(ident: String): TempPerson? =
        sessionOf(dataSource).use { session ->
            session.transaction { tx ->
                val query = queryOf("SELECT ident, status FROM temp_person WHERE ident = ?", ident)
                val rowMapper: (Row) -> TempPerson? = { row ->
                    TempPerson(
                        ident = row.string("ident"),
                        status = TempPersonStatus.valueOf(row.string("status")),
                    )
                }
                tx.run(query.map(rowMapper).asSingle)
            }
        }

    override fun lagrePerson(person: TempPerson) {
        if (hentPerson(person.ident) != null) {
            logger.warn { "Personen eksiterer allerede" }
            return
        }
        sessionOf(dataSource).use { session ->
            session.transaction { tx ->
                val query =
                    queryOf(
                        "INSERT INTO temp_person (ident, status, oppdatert) VALUES (?, ?, CURRENT_TIMESTAMP)",
                        person.ident,
                        person.status.name,
                    )
                tx.run(query.asUpdate)
            }
        }
    }

    override fun oppdaterPerson(person: TempPerson): TempPerson? =
        sessionOf(dataSource).use { session ->
            session.transaction { tx ->
                val query =
                    queryOf(
                        "UPDATE temp_person SET status = ?, oppdatert = CURRENT_TIMESTAMP WHERE ident = ? RETURNING ident, status, oppdatert",
                        person.status.name,
                        person.ident,
                    )
                val rowMapper: (Row) -> TempPerson? = { row ->
                    TempPerson(
                        ident = row.string("ident"),
                        status = TempPersonStatus.valueOf(row.string("status")),
                    )
                }
                tx.run(query.map(rowMapper).asSingle)
            }
        }

    override fun slettPerson(ident: String) {
        sessionOf(dataSource).use { session ->
            session.transaction { tx ->
                val query = queryOf("DELETE FROM temp_person WHERE ident = ?", ident)
                tx.run(query.asUpdate)
            }
        }
    }

    override fun hentAlleIdenter(): List<String> =
        sessionOf(dataSource).use { session ->
            session.transaction { tx ->
                val query = queryOf("SELECT ident FROM temp_person")
                val rowMapper: (Row) -> String = { row -> row.string("ident") }
                tx.run(query.map(rowMapper).asList)
            }
        }

    override fun hentIdenterMedStatus(
        status: TempPersonStatus,
        batchSize: Int,
    ): List<String> =
        sessionOf(dataSource).use { session ->
            session.transaction { tx ->
                val query = queryOf("SELECT ident FROM temp_person WHERE status = ? LIMIT ?", status.name, batchSize)
                val rowMapper: (Row) -> String = { row -> row.string("ident") }
                tx.run(query.map(rowMapper).asList)
            }
        }

    override fun isEmpty(): Boolean =
        sessionOf(dataSource).use { session ->
            (
                session.run(
                    queryOf("SELECT COUNT(*) FROM temp_person")
                        .map { it.int(1) }
                        .asSingle,
                ) ?: 0
            ) == 0
        }

    override fun syncPersoner() {
        sessionOf(dataSource).use { session ->
            session.transaction { tx ->
                val query =
                    queryOf(
                        """
                                INSERT INTO temp_person (ident, status)
                                SELECT ident, 'IKKE_PABEGYNT'
                                FROM person;
                """,
                    )
                val rowsAffected = tx.run(query.asUpdate)
                if (rowsAffected == 0) {
                    throw IllegalStateException("No rows were inserted into temp_person.")
                }
            }
        }
    }
}
