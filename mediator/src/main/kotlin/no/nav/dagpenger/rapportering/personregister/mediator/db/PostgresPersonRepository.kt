package no.nav.dagpenger.rapportering.personregister.mediator.db

import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Row
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.dagpenger.rapportering.personregister.mediator.Configuration.defaultObjectMapper
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.ActionTimer
import no.nav.dagpenger.rapportering.personregister.modell.AnnenMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.ArbeidssøkerHendelse
import no.nav.dagpenger.rapportering.personregister.modell.DagpengerMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.Hendelse
import no.nav.dagpenger.rapportering.personregister.modell.MeldepliktHendelse
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.Status
import no.nav.dagpenger.rapportering.personregister.modell.SøknadHendelse
import no.nav.dagpenger.rapportering.personregister.modell.TemporalCollection
import java.time.LocalDateTime
import java.util.UUID
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
                        .map { it.boolean(1) }
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
                INSERT INTO hendelse (person_id, dato, start_dato, slutt_dato, kilde,referanse_id, type, extra) 
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (referanse_id) 
                DO UPDATE SET 
                    person_id = EXCLUDED.person_id,
                    dato = EXCLUDED.dato,
                    start_dato = EXCLUDED.start_dato,
                    slutt_dato = EXCLUDED.slutt_dato,
                    kilde = EXCLUDED.kilde,
                    type = EXCLUDED.type,
                    extra = EXCLUDED.extra
                """,
                        personId,
                        hendelse.dato,
                        hendelse.hentStartDato(),
                        hendelse.hentSluttDato(),
                        hendelse.kilde.name,
                        hendelse.referanseId,
                        hendelse::class.simpleName,
                        hendelse.hentEkstrafelter(),
                    ).asUpdate,
                )
            }
        }
    }

    private fun Hendelse.hentStartDato(): LocalDateTime? =
        when (this) {
            is DagpengerMeldegruppeHendelse -> this.startDato
            is AnnenMeldegruppeHendelse -> this.startDato
            is MeldepliktHendelse -> this.startDato
            is ArbeidssøkerHendelse -> this.startDato
            is SøknadHendelse -> null
        }

    private fun Hendelse.hentSluttDato(): LocalDateTime? =
        when (this) {
            is DagpengerMeldegruppeHendelse -> this.sluttDato
            is AnnenMeldegruppeHendelse -> this.sluttDato
            is MeldepliktHendelse -> this.sluttDato
            is ArbeidssøkerHendelse -> this.sluttDato
            is SøknadHendelse -> null
        }

    private fun Hendelse.hentEkstrafelter(): String? =
        when (this) {
            is DagpengerMeldegruppeHendelse ->
                defaultObjectMapper.writeValueAsString(
                    MeldegruppeKodeExtra(meldegruppeKode = this.meldegruppeKode),
                )
            is AnnenMeldegruppeHendelse ->
                defaultObjectMapper.writeValueAsString(
                    MeldegruppeKodeExtra(meldegruppeKode = this.meldegruppeKode),
                )
            is MeldepliktHendelse ->
                defaultObjectMapper.writeValueAsString(
                    MeldepliktExtra(statusMeldeplikt = this.statusMeldeplikt),
                )
            is ArbeidssøkerHendelse -> null
            is SøknadHendelse -> null
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
        val ident = row.string("person_id") // TODO: PersonId er ikke ident!
        val dato = row.localDateTime("dato")
        val startDato = row.localDateTimeOrNull("start_dato")
        val sluttDato = row.localDateTimeOrNull("slutt_dato")
        val referanseId = row.string("referanse_id")
        val extra = row.stringOrNull("extra")

        return when (type) {
            "SøknadHendelse" -> SøknadHendelse(ident, dato, referanseId)
            "DagpengerMeldegruppeHendelse" ->
                DagpengerMeldegruppeHendelse(
                    ident,
                    dato,
                    startDato!!,
                    sluttDato,
                    defaultObjectMapper.readValue<MeldegruppeKodeExtra>(extra!!).meldegruppeKode,
                    referanseId,
                )
            "AnnenMeldegruppeHendelse" ->
                AnnenMeldegruppeHendelse(
                    ident,
                    dato,
                    startDato!!,
                    sluttDato,
                    defaultObjectMapper.readValue<MeldegruppeKodeExtra>(extra!!).meldegruppeKode,
                    referanseId,
                )
            "MeldepliktHendelse" ->
                MeldepliktHendelse(
                    ident,
                    dato,
                    startDato!!,
                    sluttDato,
                    defaultObjectMapper.readValue<MeldepliktExtra>(extra!!).statusMeldeplikt,
                    referanseId,
                )
            "ArbeidssøkerHendelse" ->
                ArbeidssøkerHendelse(
                    ident,
                    UUID.fromString(referanseId),
                    startDato!!,
                    sluttDato,
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

data class MeldegruppeKodeExtra(
    val meldegruppeKode: String,
)

data class MeldepliktExtra(
    val statusMeldeplikt: Boolean,
)
