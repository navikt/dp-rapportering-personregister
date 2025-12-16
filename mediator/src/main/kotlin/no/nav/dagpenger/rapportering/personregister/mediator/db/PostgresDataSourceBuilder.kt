package no.nav.dagpenger.rapportering.personregister.mediator.db

import ch.qos.logback.core.util.OptionHelper.getEnv
import ch.qos.logback.core.util.OptionHelper.getSystemProperty
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.configuration.FluentConfiguration

internal object PostgresDataSourceBuilder {
    const val DB_URL_KEY = "DB_JDBC_URL"

    private fun getOrThrow(key: String): String = getEnv(key) ?: getSystemProperty(key)

    val dataSource by lazy {
        HikariDataSource().apply {
            jdbcUrl = getOrThrow(DB_URL_KEY)
            maximumPoolSize = 40
            minimumIdle = 1
            idleTimeout = 10000 // 10s
            connectionTimeout = 5000 // 5s
            maxLifetime = 30000 // 30s
            initializationFailTimeout = 5000 // 5s
            addDataSourceProperty("tcpKeepAlive", true)
            addDataSourceProperty("socketTimeout", 30)
        }
    }

    private val flyWayBuilder: FluentConfiguration = Flyway.configure().connectRetries(5)

    fun clean() =
        flyWayBuilder
            .cleanDisabled(false)
            .dataSource(dataSource)
            .load()
            .clean()

    internal fun runMigration(initSql: String? = null): Int =
        flyWayBuilder
            .dataSource(dataSource)
            .initSql(initSql)
            .load()
            .migrate()
            .migrations
            .size
}
