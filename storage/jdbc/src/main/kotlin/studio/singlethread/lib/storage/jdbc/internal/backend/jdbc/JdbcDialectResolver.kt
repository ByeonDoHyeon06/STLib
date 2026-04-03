package studio.singlethread.lib.storage.jdbc.internal.backend.jdbc

import studio.singlethread.lib.storage.api.config.DatabaseConfig

internal object JdbcDialectResolver {
    fun resolve(databaseConfig: DatabaseConfig): JdbcDialect {
        return when (databaseConfig) {
            is DatabaseConfig.SQLite -> JdbcDialect.sqlite(databaseConfig)
            is DatabaseConfig.MySql -> JdbcDialect.mysql(databaseConfig)
            is DatabaseConfig.PostgreSql -> JdbcDialect.postgres(databaseConfig)
            else -> error("Unsupported database config for JDBC: ${databaseConfig::class.qualifiedName}")
        }
    }
}
