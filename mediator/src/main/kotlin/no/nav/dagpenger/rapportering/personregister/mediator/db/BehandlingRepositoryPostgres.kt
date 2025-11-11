package no.nav.dagpenger.rapportering.personregister.mediator.db

import kotliquery.queryOf
import kotliquery.sessionOf
import javax.sql.DataSource

class BehandlingRepositoryPostgres(
    private val dataSource: DataSource,
) : BehandlingRepository {
    override fun lagreData(
        behandlingId: String,
        søknadId: String,
        ident: String,
        sakId: String,
    ) = sessionOf(dataSource)
        .use { session ->
            session
                .run(
                    queryOf(
                        "INSERT INTO behandling " +
                            "(behandling_id, soknad_id, ident, sak_id) " +
                            "VALUES (?, ?, ?, ?)",
                        behandlingId,
                        søknadId,
                        ident,
                        sakId,
                    ).asUpdate,
                )
        }.let { if (it == 0) throw Exception("Lagring av behandling feilet") }

    override fun hentSisteSakId(ident: String): String? =
        sessionOf(dataSource).use { session ->
            session.run(
                queryOf(
                    "SELECT sak_id " +
                        "FROM behandling " +
                        "WHERE ident = ? " +
                        "ORDER BY tidspunkt DESC " +
                        "LIMIT 1",
                    ident,
                ).map {
                    it.string("sak_id")
                }.asSingle,
            )
        }
}
