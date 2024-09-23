package no.nav.dagpenger.rapportering.personregister.mediator.db

import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.dagpenger.rapportering.personregister.modell.Hendelse
import no.nav.dagpenger.rapportering.personregister.modell.Kildesystem
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.Status
import no.nav.dagpenger.rapportering.personregister.modell.TemporalCollection
import java.time.LocalDateTime
import javax.sql.DataSource

class PostgresPersonRepository(
    private val dataSource: DataSource,
) : PersonRepository {
    override fun finn(ident: String): Person? =
        using(sessionOf(dataSource)) { session ->
            val hendelser =
                session.run(
                    queryOf(
                        "SELECT * FROM hendelse WHERE ident = :ident",
                        mapOf("ident" to ident),
                    ).map { row ->
                        Hendelse(
                            id = row.uuid("id"),
                            ident = row.string("ident"),
                            referanseId = row.string("referanse_id"),
                            dato = row.localDateTime("dato"),
                            status = Status.valueOf(row.string("status")),
                            kilde = Kildesystem.valueOf(row.string("kilde")),
                        )
                    }.asList,
                )

            if (hendelser.isNotEmpty()) {
                Person(ident).apply {
                    hendelser.forEach { hendelse ->
                        this.hendelser.add(hendelse)
                    }

                    hentStatusHistorikk(dataSource, ident).allItems().forEach { (dato, status) ->
                        this.statusHistorikk.put(dato, status)
                    }
                }
            } else {
                null
            }
        }

    override fun lagre(person: Person) {
        using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf(
                    "INSERT INTO person (ident) VALUES (:ident)",
                    mapOf("ident" to person.ident),
                ).asUpdate,
            )
        }
        lagreStatus(dataSource, person.ident, LocalDateTime.now(), person.status)
        person.hendelser.forEach { hendelse ->
            using(sessionOf(dataSource)) { session ->
                session.run(
                    queryOf(
                        "INSERT INTO hendelse (id, ident, referanse_id, dato, status, kilde) VALUES (?, ?, ?, ?, ?, ?)",
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

    override fun oppdater(person: Person) {
        TODO("Not yet implemented")
    }
}

fun lagreStatus(
    dataSource: DataSource,
    personIdent: String,
    dato: LocalDateTime,
    status: Status,
) {
    using(sessionOf(dataSource)) { session ->
        session.run(
            queryOf(
                "INSERT INTO status_historikk (person_ident, dato, status) VALUES (:person_ident, :dato, :status)",
                mapOf("person_ident" to personIdent, "dato" to dato, "status" to status.toString()),
            ).asUpdate,
        )
    }
}

fun hentStatusHistorikk(
    dataSource: DataSource,
    personIdent: String,
): TemporalCollection<Status> =
    using(sessionOf(dataSource)) { session ->
        val statusHistorikk = TemporalCollection<Status>()
        session.run(
            queryOf(
                "SELECT * FROM status_historikk WHERE person_ident = :person_ident",
                mapOf("person_ident" to personIdent),
            ).map { row ->
                val dato = row.localDateTime("dato")
                val status = Status.valueOf(row.string("status"))
                statusHistorikk.put(dato, status)
            }.asList,
        )
        statusHistorikk
    }
