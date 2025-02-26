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
import no.nav.dagpenger.rapportering.personregister.modell.Arbeidssøkerperiode
import no.nav.dagpenger.rapportering.personregister.modell.DagpengerMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.Hendelse
import no.nav.dagpenger.rapportering.personregister.modell.MeldepliktHendelse
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.StartetArbeidssøkerperiodeHendelse
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
            val arbeidssøkerperioder = hentArbeidssøkerperioder(personId, ident)
            val hendelser = hentHendelser(personId, ident)
            val statusHistorikk = hentStatusHistorikk(personId).allItems()
            val meldeplikt = hentMeldeplikt(ident)
            val meldegruppe = hentMeldegruppe(ident)

            if (hendelser.isNotEmpty()) {
                Person(ident).apply {
                    this.meldeplikt = meldeplikt
                    this.meldegruppe = meldegruppe
                    arbeidssøkerperioder.forEach { this.arbeidssøkerperioder.add(it) }
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
                                "INSERT INTO person (ident, status, meldeplikt, meldegruppe) VALUES (:ident, :status, :meldeplikt, :meldegruppe) RETURNING id",
                                mapOf(
                                    "ident" to person.ident,
                                    "status" to person.status.type.name,
                                    "meldeplikt" to person.meldeplikt,
                                    "meldegruppe" to person.meldegruppe,
                                ),
                            ).map { row -> row.long("id") }
                                .asSingle,
                        )
                    }
                } ?: throw IllegalStateException("Klarte ikke å lagre person")

            person.arbeidssøkerperioder.forEach { lagreArbeidssøkerperiode(personId, it) }
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
            using(sessionOf(dataSource)) { session ->
                session.transaction { tx ->
                    tx.run(
                        queryOf(
                            "UPDATE person SET status = :status, meldeplikt = :meldeplikt, meldegruppe = :meldegruppe WHERE id = :id",
                            mapOf(
                                "id" to personId,
                                "status" to person.status.type.name,
                                "meldeplikt" to person.meldeplikt,
                                "meldegruppe" to person.meldegruppe,
                            ),
                        ).asUpdate,
                    )
                }
            }
            person.arbeidssøkerperioder.forEach { lagreArbeidssøkerperiode(personId, it) }
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
                VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb)
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
            is StartetArbeidssøkerperiodeHendelse -> this.startet
            is SøknadHendelse -> null
            else -> null
        }

    private fun Hendelse.hentSluttDato(): LocalDateTime? =
        when (this) {
            is DagpengerMeldegruppeHendelse -> this.sluttDato
            is AnnenMeldegruppeHendelse -> this.sluttDato
            is MeldepliktHendelse -> this.sluttDato
            is ArbeidssøkerHendelse -> this.sluttDato
            is SøknadHendelse -> null
            else -> null
        }

    private fun Hendelse.hentEkstrafelter(): String? {
        val test =
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
                else -> null
            }

        return test
    }

    private fun hentMeldegruppe(ident: String): String? =
        using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf("SELECT meldegruppe FROM person WHERE ident = :ident", mapOf("ident" to ident))
                    .map { row -> row.stringOrNull("meldegruppe") }
                    .asSingle,
            )
        }

    private fun hentMeldeplikt(ident: String): Boolean =
        using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf("SELECT meldeplikt FROM person WHERE ident = :ident", mapOf("ident" to ident))
                    .map { row -> row.boolean("meldeplikt") }
                    .asSingle,
            ) ?: throw IllegalStateException("Klarte ikke å hente meldeplikt")
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
    ): Hendelse {
        val type = row.string("type")
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
            "StartetArbeidssøkerperiodeHendelse" ->
                StartetArbeidssøkerperiodeHendelse(
                    UUID.fromString(referanseId),
                    ident,
                    startDato!!,
                )
            else -> throw IllegalArgumentException("Unknown type: $type")
        }
    }

    fun hentArbeidssøkerperioder(
        personId: Long,
        ident: String,
    ): List<Arbeidssøkerperiode> =
        using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf(
                    """
                    SELECT * 
                    FROM arbeidssoker
                    WHERE person_id = :person_id
                    """.trimIndent(),
                    mapOf("person_id" to personId),
                ).map { tilArbeidsøkerperiode(it, ident) }
                    .asList,
            )
        }

    private fun tilArbeidsøkerperiode(
        row: Row,
        ident: String,
    ): Arbeidssøkerperiode =
        Arbeidssøkerperiode(
            periodeId = row.uuid("periode_id"),
            ident = ident,
            startet = row.localDateTime("startet"),
            avsluttet = row.localDateTimeOrNull("avsluttet"),
            overtattBekreftelse = row.stringOrNull("overtatt_bekreftelse").toBooleanOrNull(),
        )

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

    private fun lagreArbeidssøkerperiode(
        personId: Long,
        arbeidssøkerperiode: Arbeidssøkerperiode,
    ) {
        using(sessionOf(dataSource)) { session ->
            session.transaction { tx ->
                tx
                    .run(
                        queryOf(
                            """
                            INSERT INTO arbeidssoker (periode_id, person_id, startet, avsluttet, overtatt_bekreftelse, sist_endret)
                            VALUES (:periode_id, :person_id, :startet, :avsluttet, :overtatt_bekreftelse, :sist_endret)
                            """.trimIndent(),
                            mapOf(
                                "periode_id" to arbeidssøkerperiode.periodeId,
                                "person_id" to personId,
                                "startet" to arbeidssøkerperiode.startet,
                                "avsluttet" to arbeidssøkerperiode.avsluttet,
                                "overtatt_bekreftelse" to arbeidssøkerperiode.overtattBekreftelse,
                                "sist_endret" to LocalDateTime.now(),
                            ),
                        ).asUpdate,
                    )
            }
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

private fun String?.toBooleanOrNull(): Boolean? = this?.let { this == "t" }

data class MeldegruppeKodeExtra(
    val meldegruppeKode: String,
)

data class MeldepliktExtra(
    val statusMeldeplikt: Boolean,
)
