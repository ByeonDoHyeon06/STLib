package studio.singlethread.lib.storage.jdbc.internal.datasource

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import studio.singlethread.lib.storage.api.config.DatabaseConfig
import studio.singlethread.lib.storage.jdbc.internal.backend.jdbc.JdbcDialectResolver
import java.util.concurrent.ConcurrentHashMap

internal class DataSourceRegistry : AutoCloseable {
    private val dataSources = ConcurrentHashMap<DatabaseConfig, HikariDataSource>()

    fun getOrCreate(databaseConfig: DatabaseConfig): HikariDataSource {
        return dataSources.computeIfAbsent(databaseConfig) { createDataSource(databaseConfig) }
    }

    override fun close() {
        dataSources.values.forEach { runCatching { it.close() } }
        dataSources.clear()
    }

    private fun createDataSource(databaseConfig: DatabaseConfig): HikariDataSource {
        val dialect = JdbcDialectResolver.resolve(databaseConfig)

        val hikariConfig = HikariConfig().apply {
            jdbcUrl = dialect.jdbcUrl
            driverClassName = dialect.driverClassName
            username = dialect.username
            password = dialect.password
            maximumPoolSize = databaseConfig.maxPoolSize
            minimumIdle = databaseConfig.minIdle
            connectionTimeout = databaseConfig.connectionTimeout.toMillis()
            idleTimeout = databaseConfig.idleTimeout.toMillis()
            maxLifetime = databaseConfig.maxLifetime.toMillis()
            poolName = "stlib-${databaseConfig::class.simpleName}-pool"
            isAutoCommit = true

            if (databaseConfig is DatabaseConfig.SQLite) {
                addDataSourceProperty("journal_mode", "WAL")
            }
        }

        return HikariDataSource(hikariConfig)
    }
}
