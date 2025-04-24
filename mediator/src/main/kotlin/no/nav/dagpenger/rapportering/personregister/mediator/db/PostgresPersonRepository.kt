package no.nav.dagpenger.rapportering.personregister.mediator.db

import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Row
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.dagpenger.rapportering.personregister.mediator.Configuration.defaultObjectMapper
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.ActionTimer
import no.nav.dagpenger.rapportering.personregister.modell.AnnenMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.Arbeidssøkerperiode
import no.nav.dagpenger.rapportering.personregister.modell.ArbeidssøkerperiodeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.AvsluttetArbeidssøkerperiodeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.DagpengerMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.Hendelse
import no.nav.dagpenger.rapportering.personregister.modell.Kildesystem
import no.nav.dagpenger.rapportering.personregister.modell.MeldepliktHendelse
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.PersonSynkroniseringHendelse
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
            val statusHistorikk = hentStatusHistorikk(personId)
            val meldeplikt = hentMeldeplikt(ident)
            val meldegruppe = hentMeldegruppe(ident)

            Person(
                ident = ident,
                statusHistorikk = statusHistorikk,
                arbeidssøkerperioder = arbeidssøkerperioder.toMutableList(),
            ).apply {
                this.meldeplikt = meldeplikt
                this.meldegruppe = meldegruppe
                hendelser.forEach { this.hendelser.add(it) }
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
                                    "status" to person.status.name,
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
                .getAll()
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
                                "status" to person.status.name,
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
                .getAll()
                .forEach { (dato, status) ->
                    lagreStatusHistorikk(personId, dato, status)
                }
        }

    override fun hentAntallPersoner(): Int =
        actionTimer.timedAction("db-hentAntallPersoner") {
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

    override fun hentAntallFremtidigeHendelser(): Int =
        actionTimer.timedAction("db-hentAntallFremtidigeHendelser") {
            using(sessionOf(dataSource)) { session ->
                session.run(
                    queryOf("SELECT COUNT(*) FROM fremtidig_hendelse")
                        .map { it.int(1) }
                        .asSingle,
                ) ?: 0
            }
        }

    override fun hentAntallDagpengebrukere(): Int =
        actionTimer.timedAction("db-hentAntallDagpengebrukere") {
            using(sessionOf(dataSource)) { session ->
                session.run(
                    queryOf("SELECT COUNT(*) FROM person WHERE status = 'DAGPENGERBRUKER'")
                        .map { it.int(1) }
                        .asSingle,
                ) ?: 0
            }
        }

    override fun hentAntallOvetagelser(): Int =
        actionTimer.timedAction("db-hentAntallOvertagelser") {
            using(sessionOf(dataSource)) { session ->
                session.run(
                    queryOf("SELECT COUNT(*) FROM arbeidssoker WHERE overtatt_bekreftelse = true")
                        .map { it.int(1) }
                        .asSingle,
                ) ?: 0
            }
        }

    override fun lagreFremtidigHendelse(hendelse: Hendelse) =
        actionTimer.timedAction("db-lagreFremtidigHendelse") {
            using(sessionOf(dataSource)) { session ->
                session.transaction { tx ->
                    tx.run(
                        queryOf(
                            """
                INSERT INTO fremtidig_hendelse (ident, dato, start_dato, slutt_dato, kilde,referanse_id, type, extra) 
                VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                ON CONFLICT (referanse_id) 
                DO UPDATE SET 
                    ident = EXCLUDED.ident,
                    dato = EXCLUDED.dato,
                    start_dato = EXCLUDED.start_dato,
                    slutt_dato = EXCLUDED.slutt_dato,
                    kilde = EXCLUDED.kilde,
                    type = EXCLUDED.type,
                    extra = EXCLUDED.extra
                """,
                            hendelse.ident,
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
            }.validateRowsAffected()
        }

    override fun hentHendelserSomSkalAktiveres(): List<Hendelse> =
        actionTimer.timedAction("db-hentHendelserSomSkalAktiveres") {
            using(sessionOf(dataSource)) { session ->
                session.run(
                    queryOf(
                        """
                        SELECT *
                        FROM fremtidig_hendelse
                        WHERE DATE(start_dato) <= current_date
                        """.trimIndent(),
                    ).map { tilHendelse(it, it.string("ident")) }
                        .asList,
                )
            }
        }

    override fun slettFremtidigHendelse(referanseId: String) =
        actionTimer.timedAction("db-slettFremtidigHendelse") {
            using(sessionOf(dataSource)) { session ->
                session.transaction { tx ->
                    tx.run(
                        queryOf(
                            "DELETE FROM fremtidig_hendelse WHERE referanse_id = :referanse_id",
                            mapOf(
                                "referanse_id" to referanseId,
                            ),
                        ).asUpdate,
                    )
                }
            }.validateRowsAffected()
        }

    override fun hentPersonerMedDagpenger(): List<String> =
        using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf(
                    """
                    SELECT ident FROM person p 
                    INNER JOIN arbeidssoker arbs on p.id = arbs.person_id 
                    WHERE p.status = 'DAGPENGERBRUKER' AND arbs.overtatt_bekreftelse IS NOT true AND arbs.avsluttet IS null
                    """.trimIndent(),
                ).map { it.string("ident") }
                    .asList,
            )
        }

    override fun hentPersonerSomKanSlettes(): List<String> =
        using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf(
                    """
                    SELECT ident FROM person p 
                    WHERE p.status = 'IKKE_DAGPENGERBRUKER'
                    AND (SELECT count(*) FROM hendelse h WHERE h.person_id = p.id AND h.dato > CURRENT_DATE - INTERVAL '60 days') = 0
                    AND (
                        SELECT count(*)
                        FROM fremtidig_hendelse fh
                        WHERE fh.ident = p.ident
                            AND NOT (fh.extra @> '{"statusMeldeplikt": false}'::jsonb)
                            AND NOT (fh.extra @> '{"meldegruppeKode": "ARBS"}'::jsonb)
                    ) = 0
                    """.trimIndent(),
                ).map { it.string("ident") }
                    .asList,
            )
        }

    override fun slettPerson(ident: String) =
        actionTimer.timedAction("db-slettPerson") {
            val personId = hentPersonId(ident)

            using(sessionOf(dataSource)) { session ->
                session.transaction { tx ->
                    tx.run(
                        queryOf(
                            "DELETE FROM status_historikk WHERE person_id = :person_id",
                            mapOf(
                                "person_id" to personId,
                            ),
                        ).asUpdate,
                    )
                    tx.run(
                        queryOf(
                            "DELETE FROM hendelse WHERE person_id = :person_id",
                            mapOf(
                                "person_id" to personId,
                            ),
                        ).asUpdate,
                    )
                    tx.run(
                        queryOf(
                            "DELETE FROM arbeidssoker WHERE person_id = :person_id",
                            mapOf(
                                "person_id" to personId,
                            ),
                        ).asUpdate,
                    )
                    tx.run(
                        queryOf(
                            "DELETE FROM person WHERE ident = :ident",
                            mapOf(
                                "ident" to ident,
                            ),
                        ).asUpdate,
                    )
                }
            }.validateRowsAffected()
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
    ) = actionTimer.timedAction("db-lagreHendelse") {
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
        }.validateRowsAffected()
    }

    private fun Hendelse.hentStartDato(): LocalDateTime? =
        when (this) {
            is DagpengerMeldegruppeHendelse -> this.startDato
            is AnnenMeldegruppeHendelse -> this.startDato
            is MeldepliktHendelse -> this.startDato
            is StartetArbeidssøkerperiodeHendelse -> this.startet
            is AvsluttetArbeidssøkerperiodeHendelse -> this.startet
            is PersonSynkroniseringHendelse -> this.startDato
            is SøknadHendelse -> null
            else -> null
        }

    private fun Hendelse.hentSluttDato(): LocalDateTime? =
        when (this) {
            is DagpengerMeldegruppeHendelse -> this.sluttDato
            is AnnenMeldegruppeHendelse -> this.sluttDato
            is MeldepliktHendelse -> this.sluttDato
            is StartetArbeidssøkerperiodeHendelse -> null
            is AvsluttetArbeidssøkerperiodeHendelse -> this.avsluttet
            is SøknadHendelse -> null
            else -> null
        }

    private fun Hendelse.hentEkstrafelter(): String? {
        val test =
            when (this) {
                is DagpengerMeldegruppeHendelse ->
                    defaultObjectMapper.writeValueAsString(
                        MeldegruppeExtra(meldegruppeKode = this.meldegruppeKode, harMeldtSeg = this.harMeldtSeg),
                    )

                is AnnenMeldegruppeHendelse ->
                    defaultObjectMapper.writeValueAsString(
                        MeldegruppeExtra(meldegruppeKode = this.meldegruppeKode, harMeldtSeg = this.harMeldtSeg),
                    )

                is MeldepliktHendelse ->
                    defaultObjectMapper.writeValueAsString(
                        MeldepliktExtra(statusMeldeplikt = this.statusMeldeplikt, harMeldtSeg = this.harMeldtSeg),
                    )

                is ArbeidssøkerperiodeHendelse -> null
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
        val kilde = row.string("kilde")

        return when (type) {
            "SøknadHendelse" -> SøknadHendelse(ident, dato, referanseId)
            "DagpengerMeldegruppeHendelse" -> {
                val meldegruppeExtra = defaultObjectMapper.readValue<MeldegruppeExtra>(extra!!)
                DagpengerMeldegruppeHendelse(
                    ident = ident,
                    dato = dato,
                    referanseId = referanseId,
                    startDato =
                        startDato ?: throw IllegalStateException(
                            "DagpengerMeldegruppeHendelse med referanseId $referanseId mangler startDato",
                        ),
                    sluttDato = sluttDato,
                    meldegruppeKode = meldegruppeExtra.meldegruppeKode,
                    harMeldtSeg = meldegruppeExtra.harMeldtSeg ?: true,
                    kilde = Kildesystem.valueOf(kilde),
                )
            }
            "AnnenMeldegruppeHendelse" -> {
                val meldegruppeExtra = defaultObjectMapper.readValue<MeldegruppeExtra>(extra!!)
                AnnenMeldegruppeHendelse(
                    ident = ident,
                    dato = dato,
                    referanseId = referanseId,
                    startDato =
                        startDato ?: throw IllegalStateException(
                            "AnnenMeldegruppeHendelse med referanseId $referanseId mangler startDato",
                        ),
                    sluttDato = sluttDato,
                    meldegruppeKode = meldegruppeExtra.meldegruppeKode,
                    harMeldtSeg = meldegruppeExtra.harMeldtSeg ?: true,
                )
            }
            "MeldepliktHendelse" -> {
                val meldepliktExtra = defaultObjectMapper.readValue<MeldepliktExtra>(extra!!)
                MeldepliktHendelse(
                    ident = ident,
                    dato = dato,
                    referanseId = referanseId,
                    startDato =
                        startDato ?: throw IllegalStateException(
                            "MeldepliktHendelse med referanseId $referanseId mangler startDato",
                        ),
                    sluttDato = sluttDato,
                    statusMeldeplikt = meldepliktExtra.statusMeldeplikt,
                    kilde = Kildesystem.valueOf(kilde),
                    harMeldtSeg = meldepliktExtra.harMeldtSeg ?: true,
                )
            }
            "StartetArbeidssøkerperiodeHendelse" ->
                StartetArbeidssøkerperiodeHendelse(
                    periodeId = UUID.fromString(referanseId),
                    ident = ident,
                    startet =
                        startDato ?: throw IllegalStateException(
                            "StartetArbeidssøkerperiodeHendelse med referanseId $referanseId mangler startDato",
                        ),
                )
            "AvsluttetArbeidssøkerperiodeHendelse" ->
                AvsluttetArbeidssøkerperiodeHendelse(
                    periodeId = UUID.fromString(referanseId),
                    ident = ident,
                    startet =
                        startDato ?: throw IllegalStateException(
                            "AvsluttetArbeidssøkerperiodeHendelse med referanseId $referanseId mangler startDato",
                        ),
                    avsluttet =
                        sluttDato ?: throw IllegalStateException(
                            "AvsluttetArbeidssøkerperiodeHendelse med referanseId $referanseId mangler sluttDato",
                        ),
                )
            "PersonSynkroniseringHendelse" ->
                PersonSynkroniseringHendelse(
                    ident = ident,
                    dato = dato,
                    referanseId = referanseId,
                    startDato = startDato ?: throw IllegalStateException("PersonSynkroniseringHendelse mangler startDato"),
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
                        val status = Status.valueOf(row.string("status"))
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
            session
                .transaction { tx ->
                    tx
                        .run(
                            queryOf(
                                """
                                INSERT INTO arbeidssoker (periode_id, person_id, startet, avsluttet, overtatt_bekreftelse, sist_endret)
                                VALUES (:periode_id, :person_id, :startet, :avsluttet, :overtatt_bekreftelse, :sist_endret)
                                ON CONFLICT (periode_id) 
                                DO UPDATE SET 
                                    person_id = EXCLUDED.person_id,
                                    startet = EXCLUDED.startet,
                                    avsluttet = EXCLUDED.avsluttet,
                                    overtatt_bekreftelse = EXCLUDED.overtatt_bekreftelse,
                                    sist_endret = CASE 
                                        WHEN EXCLUDED.person_id IS DISTINCT FROM arbeidssoker.person_id
                                            OR EXCLUDED.startet IS DISTINCT FROM arbeidssoker.startet
                                            OR EXCLUDED.avsluttet IS DISTINCT FROM arbeidssoker.avsluttet
                                            OR EXCLUDED.overtatt_bekreftelse IS DISTINCT FROM arbeidssoker.overtatt_bekreftelse
                                        THEN EXCLUDED.sist_endret
                                        ELSE arbeidssoker.sist_endret
                                    END
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
                }.validateRowsAffected()
        }
    }

    private fun lagreStatusHistorikk(
        personId: Long,
        dato: LocalDateTime,
        status: Status,
    ) {
        using(sessionOf(dataSource)) { session ->
            session
                .transaction { tx ->
                    tx.run(
                        queryOf(
                            """
                            INSERT INTO status_historikk (person_id, dato, status)
                            VALUES (:person_id, :dato, :status)
                            ON CONFLICT (person_id, dato) DO NOTHING
                            """.trimIndent(),
                            mapOf("person_id" to personId, "dato" to dato, "status" to status.name),
                        ).asUpdate,
                    )
                }
        }
    }
}

private fun Int.validateRowsAffected(excepted: Int = 1) {
    if (this != excepted) throw RuntimeException("Expected $excepted but got $this")
}

private fun String?.toBooleanOrNull(): Boolean? = this?.let { this == "t" }

data class MeldegruppeExtra(
    val meldegruppeKode: String,
    val harMeldtSeg: Boolean? = null,
)

data class MeldepliktExtra(
    val statusMeldeplikt: Boolean,
    val harMeldtSeg: Boolean? = null,
)
