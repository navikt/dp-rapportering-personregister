package no.nav.dagpenger.rapportering.personregister.mediator.db

import kotliquery.Row
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.ActionTimer
import no.nav.dagpenger.rapportering.personregister.modell.Arbeidssøkerperiode
import java.time.LocalDateTime
import java.util.UUID
import javax.sql.DataSource

class PostrgesArbeidssøkerRepository(
    private val dataSource: DataSource,
    private val actionTimer: ActionTimer,
) : ArbeidssøkerRepository {
    override fun hentArbeidssøkerperioder(ident: String): List<Arbeidssøkerperiode> =
        actionTimer.timedAction("db-hentArbeidssøkerperioder") {
            using(sessionOf(dataSource)) { session ->
                session.run(
                    queryOf(
                        """
                        SELECT a.* 
                        FROM arbeidssoker a
                        JOIN person p ON a.person_id = p.id
                        WHERE p.ident = :ident
                        """.trimIndent(),
                        mapOf("ident" to ident),
                    ).map { tilArbeidssokerpersiode(it, ident) }
                        .asList,
                )
            }
        }

    private fun tilArbeidssokerpersiode(
        row: Row,
        ident: String,
    ): Arbeidssøkerperiode =
        Arbeidssøkerperiode(
            periodeId = row.uuid("periode_id"),
            ident = ident,
            startet = row.localDateTime("startet"),
            avsluttet = row.localDateTimeOrNull("avsluttet"),
            overtattBekreftelse = row.string("overtatt_bekreftelse").toBooleanOrNull(),
        )

    override fun lagreArbeidssøkerperiode(arbeidssøkerperiode: Arbeidssøkerperiode) =
        actionTimer.timedAction("db-lagreArbeidssøkerperiode") {
            val personId = hentPersonId(arbeidssøkerperiode.ident) ?: throw IllegalStateException("Person ikke funnet")

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
                        ).validateRowsAffected()
                }
            }
        }

    override fun oppdaterOvertagelse(
        periodeId: UUID,
        overtattBekreftelse: Boolean,
    ) = actionTimer.timedAction("db-oppdaterOvertagelse") {
        using(sessionOf(dataSource)) { session ->
            session.transaction { tx ->
                tx
                    .run(
                        queryOf(
                            """
                            UPDATE arbeidssoker
                            SET overtatt_bekreftelse = :overtatt_bekreftelse, sist_endret = :sist_endret
                            WHERE periode_id = :periode_id
                            """.trimIndent(),
                            mapOf(
                                "periode_id" to periodeId,
                                "overtatt_bekreftelse" to overtattBekreftelse,
                                "sist_endret" to LocalDateTime.now(),
                            ),
                        ).asUpdate,
                    ).validateRowsAffected()
            }
        }
    }

    override fun avsluttArbeidssøkerperiode(
        periodeId: UUID,
        avsluttetDato: LocalDateTime,
    ) = actionTimer.timedAction("db-avsluttArbeidssøkerperiode") {
        using(sessionOf(dataSource)) { session ->
            session.transaction { tx ->
                tx
                    .run(
                        queryOf(
                            """
                            UPDATE arbeidssoker
                            SET avsluttet = :avsluttet, sist_endret = :sist_endret
                            WHERE periode_id = :periode_id
                            """.trimIndent(),
                            mapOf(
                                "periode_id" to periodeId,
                                "avsluttet" to avsluttetDato,
                                "sist_endret" to LocalDateTime.now(),
                            ),
                        ).asUpdate,
                    ).validateRowsAffected()
            }
        }
    }

    override fun oppdaterPeriodeId(
        ident: String,
        gammelPeriodeId: UUID,
        nyPeriodeId: UUID,
    ) = actionTimer.timedAction("db-oppdaterPeriodeId") {
        val personId = hentPersonId(ident) ?: throw IllegalStateException("Person ikke funnet")

        using(sessionOf(dataSource)) { session ->
            session.transaction { tx ->
                tx
                    .run(
                        queryOf(
                            """
                            UPDATE arbeidssoker
                            SET periode_id = :ny_periode_id, sist_endret = :sist_endret
                            WHERE person_id = :person_id AND periode_id = :gammel_periode_id
                            """.trimIndent(),
                            mapOf(
                                "ny_periode_id" to nyPeriodeId,
                                "person_id" to personId,
                                "gammel_periode_id" to gammelPeriodeId,
                                "sist_endret" to LocalDateTime.now(),
                            ),
                        ).asUpdate,
                    ).validateRowsAffected()
            }
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

    private fun String?.toBooleanOrNull(): Boolean? = this?.let { this == "t" }

    private fun Int.validateRowsAffected(excepted: Int = 1) {
        if (this != excepted) throw RuntimeException("Expected $excepted but got $this")
    }
}
