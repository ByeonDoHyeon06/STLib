package studio.singlethread.lib.storage.jdbc.internal.backend.jdbc

import studio.singlethread.lib.storage.api.config.DatabaseConfig

internal data class JdbcDialect(
    val jdbcUrl: String,
    val driverClassName: String,
    val createTableSql: String,
    val username: String? = null,
    val password: String? = null,
    val parameters: Map<String, String> = emptyMap(),
) {
    companion object {
        private const val TABLE_SQL =
            """
            CREATE TABLE IF NOT EXISTS st_storage_entries (
                namespace VARCHAR(120) NOT NULL,
                collection VARCHAR(120) NOT NULL,
                entry_key VARCHAR(255) NOT NULL,
                value_blob BLOB NOT NULL,
                updated_at BIGINT NOT NULL,
                PRIMARY KEY (namespace, collection, entry_key)
            )
            """

        fun sqlite(config: DatabaseConfig.SQLite): JdbcDialect {
            return JdbcDialect(
                jdbcUrl = "jdbc:sqlite:${config.filePath}",
                driverClassName = "org.sqlite.JDBC",
                createTableSql = TABLE_SQL,
            )
        }

        fun mysql(config: DatabaseConfig.MySql): JdbcDialect {
            val query = config.parameters.entries.joinToString("&") { "${it.key}=${it.value}" }
            val suffix = if (query.isBlank()) "" else "?$query"
            return JdbcDialect(
                jdbcUrl = "jdbc:mysql://${config.host}:${config.port}/${config.database}$suffix",
                driverClassName = "com.mysql.cj.jdbc.Driver",
                createTableSql = TABLE_SQL,
                username = config.username,
                password = config.password,
                parameters = config.parameters,
            )
        }

        fun postgres(config: DatabaseConfig.PostgreSql): JdbcDialect {
            val query = config.parameters.entries.joinToString("&") { "${it.key}=${it.value}" }
            val suffix = if (query.isBlank()) "" else "?$query"
            return JdbcDialect(
                jdbcUrl = "jdbc:postgresql://${config.host}:${config.port}/${config.database}$suffix",
                driverClassName = "org.postgresql.Driver",
                createTableSql = TABLE_SQL,
                username = config.username,
                password = config.password,
                parameters = config.parameters,
            )
        }
    }
}
