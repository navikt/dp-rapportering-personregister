package no.nav.dagpenger.rapportering.personregister.mediator.db

import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.dagpenger.rapportering.personregister.modell.Hendelse
import no.nav.dagpenger.rapportering.personregister.modell.Kildesystem
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.Status
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
                // Create Person with all hendelser
                Person(ident).apply {
                    hendelser.forEach { hendelse ->
                        this.hendelser.put(hendelse.dato, hendelse)
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

        person.hendelser.allItems().forEach { (date, hendelse) ->
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
