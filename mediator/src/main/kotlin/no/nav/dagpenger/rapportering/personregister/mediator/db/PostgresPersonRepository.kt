package no.nav.dagpenger.rapportering.personregister.mediator.db

import kotliquery.Row
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.ActionTimer
import no.nav.dagpenger.rapportering.personregister.modell.ArbeidssøkerHendelse
import no.nav.dagpenger.rapportering.personregister.modell.AvslagHendelse
import no.nav.dagpenger.rapportering.personregister.modell.Hendelse
import no.nav.dagpenger.rapportering.personregister.modell.InnvilgelseHendelse
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.StansHendelse
import no.nav.dagpenger.rapportering.personregister.modell.Status
import no.nav.dagpenger.rapportering.personregister.modell.SøknadHendelse
import no.nav.dagpenger.rapportering.personregister.modell.TemporalCollection
import java.time.LocalDateTime
import javax.sql.DataSource

class PostgresPersonRepository(
    private val dataSource: DataSource,
    private val actionTimer: ActionTimer,
) : PersonRepository {
    override fun hentPerson(ident: String): Person? =
        actionTimer.timedAction("db-hentPerson") {
            val personId = hentPersonId(ident) ?: return@timedAction null
            val hendelser = hentHendelser(personId)
            val statusHistorikk = hentStatusHistorikk(personId).allItems()

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
            val personId =
                using(sessionOf(dataSource)) { session ->
                    session.transaction { tx ->
                        tx.run(
                            queryOf(
                                "INSERT INTO person (ident) VALUES (:ident) RETURNING id",
                                mapOf(
                                    "ident" to person.ident,
                                ),
                            ).map { row -> row.long("id") }
                                .asSingle,
                        )
                    }
                } ?: throw IllegalStateException("Klarte ikke å lagre person")

            person.hendelser.forEach { lagreHendelse(personId, it) }
            person.statusHistorikk
                .allItems()
                .forEach { (dato, status) ->
                    lagreStatusHistorikk(personId, dato, status)
                }
        }

    override fun oppdaterPerson(person: Person) =
        actionTimer.timedAction("db-oppdaterPerson") {
            val personId = hentPersonId(person.ident) ?: throw IllegalStateException("Person finnes ikke")
            person.hendelser.forEach { lagreHendelse(personId, it) }
            person.statusHistorikk
                .allItems()
                .forEach { (dato, status) ->
                    lagreStatusHistorikk(personId, dato, status)
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
                INSERT INTO hendelse (person_id, dato, kilde,referanse_id, type) 
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (referanse_id) 
                DO UPDATE SET 
                    person_id = EXCLUDED.person_id,
                    dato = EXCLUDED.dato,
                    kilde = EXCLUDED.kilde,
                    type = EXCLUDED.type
                """,
                        personId,
                        hendelse.dato,
                        hendelse.kilde.name,
                        hendelse.referanseId,
                        hendelse::class.simpleName,
                    ).asUpdate,
                )
            }
        }
    }

    private fun hentHendelser(personId: Long): List<Hendelse> =
        using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf("SELECT * FROM hendelse WHERE person_id = :person_id", mapOf("person_id" to personId))
                    .map(::tilHendelse)
                    .asList,
            )
        }

    private fun tilHendelse(row: Row): Hendelse {
        val type = row.string("type")
        val ident = row.string("person_id")
        val dato = row.localDateTime("dato")
        val referanseId = row.string("referanse_id")

        return when (type) {
            "SøknadHendelse" -> SøknadHendelse(ident, dato, referanseId)
            "InnvilgelseHendelse" -> InnvilgelseHendelse(ident, dato, referanseId)
            "AvslagHendelse" -> AvslagHendelse(ident, dato, referanseId)
            "StansHendelse" ->
                StansHendelse(
                    ident,
                    dato,
                    row.string("meldegruppe_kode"),
                    referanseId,
                )
            "ArbeidssøkerHendelse" ->
                ArbeidssøkerHendelse(
                    ident,
                    row.uuid("periode_id"),
                    row.localDateTime("start_dato"),
                    row.localDateTimeOrNull("slutt_dato"),
                )
            else -> throw IllegalArgumentException("Unknown type: $type")
        }
    }

    private fun hentStatusHistorikk(personId: Long): TemporalCollection<Status> =
        TemporalCollection<Status>().apply {
            using(sessionOf(dataSource)) { session ->
                session.run(
                    queryOf(
                        "SELECT * FROM status_historikk WHERE person_id = :person_id",
                        mapOf("person_id" to personId),
                    ).map { row ->
                        val dato = row.localDateTime("dato")
                        val status = Status.rehydrer(row.string("status")) // TOOD: fix
                        put(dato, status)
                    }.asList,
                )
            }
        }

    private fun lagreStatusHistorikk(
        personId: Long,
        dato: LocalDateTime,
        status: Status,
    ) {
        using(sessionOf(dataSource)) { session ->
            session.transaction { tx ->
                tx.run(
                    queryOf(
                        "INSERT INTO status_historikk (person_id, dato, status) VALUES (:person_id, :dato, :status)",
                        mapOf("person_id" to personId, "dato" to dato, "status" to status.type.name),
                    ).asUpdate,
                )
            }
        }
    }
}
