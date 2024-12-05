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
import no.nav.dagpenger.rapportering.personregister.modell.TemporalCollection
import java.time.LocalDateTime
import javax.sql.DataSource

class PostgresPersonRepository(
    private val dataSource: DataSource,
    private val actionTimer: ActionTimer,
) : PersonRepository {
    override fun hentPerson(ident: String): Person? =
        actionTimer.timedAction("db-hentPerson") {
            val hendelser = hentHendelser(ident)
            val statusHistorikk = hentStatusHistorikk(ident).allItems()

            if (hendelser.isNotEmpty()) {
                Person(ident).apply {
                    hendelser.forEach { this.hendelser.add(it) }
                    statusHistorikk.forEach { (dato, status) -> this.statusHistorikk.put(dato, status) }
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
            using(sessionOf(dataSource)) { session ->
                session.transaction { tx ->
                    tx.run(
                        queryOf("INSERT INTO person (ident) VALUES (:ident)", mapOf("ident" to person.ident))
                            .asUpdate,
                    )
                }
            }

            person.hendelser.forEach { lagreHendelse(it) }
            person.statusHistorikk
                .allItems()
                .forEach { (dato, status) ->
                    lagreStatusHistorikk(person.ident, dato, status)
                }
        }

    override fun oppdaterPerson(person: Person) =
        actionTimer.timedAction("db-oppdaterPerson") {
            person.hendelser.forEach { lagreHendelse(it) }
            person.statusHistorikk
                .allItems()
                .forEach { (dato, status) ->
                    lagreStatusHistorikk(person.ident, dato, status)
                }
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

    private fun lagreHendelse(hendelse: Hendelse) {
        using(sessionOf(dataSource)) { session ->
            session.transaction { tx ->
                tx.run(
                    queryOf(
                        """
                INSERT INTO hendelse (id, ident, referanse_id, dato, status, kilde) 
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) 
                DO UPDATE SET 
                    ident = EXCLUDED.ident,
                    referanse_id = EXCLUDED.referanse_id,
                    dato = EXCLUDED.dato,
                    status = EXCLUDED.status,
                    kilde = EXCLUDED.kilde
                """,
                        hendelse.id,
                        hendelse.ident,
                        hendelse.referanseId,
                        hendelse.dato,
                        hendelse.status.name,
                        hendelse.kilde.name,
                    ).asUpdate,
                )
            }
        }
    }

    private fun hentHendelser(ident: String): List<Hendelse> =
        using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf("SELECT * FROM hendelse WHERE ident = :ident", mapOf("ident" to ident))
                    .map { row -> tilHendelse(row) }
                    .asList,
            )
        }

    private fun tilHendelse(row: Row) =
        Hendelse(
            id = row.uuid("id"),
            ident = row.string("ident"),
            referanseId = row.string("referanse_id"),
            dato = row.localDateTime("dato"),
            status = Status.valueOf(row.string("status")),
            kilde = Kildesystem.valueOf(row.string("kilde")),
        )

    private fun lagreStatusHistorikk(
        personIdent: String,
        dato: LocalDateTime,
        status: Status,
    ) {
        using(sessionOf(dataSource)) { session ->
            session.transaction { tx ->
                tx.run(
                    queryOf(
                        "INSERT INTO status_historikk (person_ident, dato, status) VALUES (:person_ident, :dato, :status)",
                        mapOf("person_ident" to personIdent, "dato" to dato, "status" to status.toString()),
                    ).asUpdate,
                )
            }
        }
    }

    private fun hentStatusHistorikk(personIdent: String): TemporalCollection<Status> =
        TemporalCollection<Status>().apply {
            using(sessionOf(dataSource)) { session ->
                session.run(
                    queryOf(
                        "SELECT * FROM status_historikk WHERE person_ident = :person_ident",
                        mapOf("person_ident" to personIdent),
                    ).map { row ->
                        val dato = row.localDateTime("dato")
                        val status = Status.valueOf(row.string("status"))
                        put(dato, status)
                    }.asList,
                )
            }
        }
}

private fun String?.toBooleanOrNull(): Boolean? = this?.let { this == "t" }
