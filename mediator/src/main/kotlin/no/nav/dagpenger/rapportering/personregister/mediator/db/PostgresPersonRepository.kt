package no.nav.dagpenger.rapportering.personregister.mediator.db

import kotliquery.Row
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.ActionTimer
import no.nav.dagpenger.rapportering.personregister.modell.Hendelse
import no.nav.dagpenger.rapportering.personregister.modell.Kildesystem
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.Status
import no.nav.dagpenger.rapportering.personregister.modell.SØKT
import javax.sql.DataSource

class PostgresPersonRepository(
    private val dataSource: DataSource,
    private val actionTimer: ActionTimer,
) : PersonRepository {
    override fun hentPerson(ident: String): Person? =
        actionTimer.timedAction("db-hentPerson") {
            val personId = hentPersonId(ident) ?: return@timedAction null
            val status = hentPersonStatus(ident) ?: return@timedAction null
            val hendelser = hentHendelser(personId, ident)

            if (hendelser.isNotEmpty()) {
                Person(ident, status).apply {
                    hendelser.forEach { this.hendelser.add(it) }
                }
            } else {
                null
            }
        }

    override fun finnesPerson(ident: String): Boolean =
        actionTimer.timedAction("db-finnesPerson") {
            using(sessionOf(dataSource)) { session ->
                session.run(
                    queryOf("SELECT EXISTS(SELECT 1 FROM person WHERE ident = :ident)", mapOf("ident" to ident))
                        .map { it.string(1).toBoolean() }
                        .asSingle,
                ) ?: false
            }
        }

    override fun lagrePerson(person: Person) =
        actionTimer.timedAction("db-lagrePerson") {
            val personId =
                using(sessionOf(dataSource)) { session ->
                    session.transaction { tx ->
                        tx.run(
                            queryOf(
                                "INSERT INTO person (ident, status) VALUES (:ident, :status) RETURNING id",
                                mapOf(
                                    "ident" to person.ident,
                                    "status" to person.status.type.name,
                                ),
                            ).map { row -> row.long("id") }
                                .asSingle,
                        )
                    }
                } ?: throw IllegalStateException("Klarte ikke å lagre person")

            person.hendelser.forEach { lagreHendelse(personId, it) }
        }

    override fun oppdaterPerson(person: Person) =
        actionTimer.timedAction("db-oppdaterPerson") {
            val personId = hentPersonId(person.ident) ?: throw IllegalStateException("Person finnes ikke")
            oppdaterStatus(person.ident, person.status)
            person.hendelser.forEach { lagreHendelse(personId, it) }
        }

    override fun hentAnallPersoner(): Int =
        actionTimer.timedAction("db-hentAnallPersoner") {
            using(sessionOf(dataSource)) { session ->
                session.run(
                    queryOf("SELECT COUNT(*) FROM person")
                        .map { it.int(1) }
                        .asSingle,
                ) ?: 0
            }
        }

    fun oppdaterStatus(
        idnet: String,
        status: Status,
    ) {
        using(sessionOf(dataSource)) { session ->
            session.transaction { tx ->
                tx.run(
                    queryOf(
                        "UPDATE person SET status = :status WHERE ident = :ident",
                        mapOf(
                            "status" to status.type.name,
                            "ident" to idnet,
                        ),
                    ).asUpdate,
                )
            }
        }
    }

    override fun hentAntallHendelser(): Int =
        actionTimer.timedAction("db-hentAntallHendelser") {
            using(sessionOf(dataSource)) { session ->
                session.run(
                    queryOf("SELECT COUNT(*) FROM hendelse")
                        .map { it.int(1) }
                        .asSingle,
                ) ?: 0
            }
        }

    private fun hentPersonStatus(ident: String): Status =
        using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf("SELECT status FROM person WHERE ident = :ident", mapOf("ident" to ident))
                    .map { row -> Status.rehydrer(row.string("status")) ?: SØKT }
                    .asSingle,
            ) ?: SØKT
        }

    private fun hentPersonId(ident: String): Long? =
        using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf("SELECT * FROM person WHERE ident = :ident", mapOf("ident" to ident))
                    .map { row -> row.long("id") }
                    .asSingle,
            )
        }

    private fun lagreHendelse(
        personId: Long,
        hendelse: Hendelse,
    ) {
        using(sessionOf(dataSource)) { session ->
            session.transaction { tx ->
                tx.run(
                    queryOf(
                        """
                INSERT INTO hendelse (id, person_id, referanse_id, dato, status, kilde) 
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) 
                DO UPDATE SET 
                    person_id = EXCLUDED.person_id,
                    referanse_id = EXCLUDED.referanse_id,
                    dato = EXCLUDED.dato,
                    status = EXCLUDED.status,
                    kilde = EXCLUDED.kilde
                """,
                        hendelse.id,
                        personId,
                        hendelse.referanseId,
                        hendelse.dato,
                        hendelse.status.name,
                        hendelse.kilde.name,
                    ).asUpdate,
                )
            }
        }
    }

    private fun hentHendelser(
        personId: Long,
        ident: String,
    ): List<Hendelse> =
        using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf("SELECT * FROM hendelse WHERE person_id = :person_id", mapOf("person_id" to personId))
                    .map { tilHendelse(it, ident) }
                    .asList,
            )
        }

    private fun tilHendelse(
        row: Row,
        ident: String,
    ) = Hendelse(
        id = row.uuid("id"),
        ident = ident,
        referanseId = row.string("referanse_id"),
        dato = row.localDateTime("dato"),
        status = Status.Type.valueOf(row.string("status")),
        kilde = Kildesystem.valueOf(row.string("kilde")),
    )
}
