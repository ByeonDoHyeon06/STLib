package studio.singlethread.lib.storage.api.config

import java.time.Duration

sealed interface DatabaseConfig {
    val maxPoolSize: Int
    val minIdle: Int
    val connectionTimeout: Duration
    val idleTimeout: Duration
    val maxLifetime: Duration

    data class Json(
        val filePath: String,
        override val maxPoolSize: Int = 1,
        override val minIdle: Int = 1,
        override val connectionTimeout: Duration = Duration.ofSeconds(5),
        override val idleTimeout: Duration = Duration.ofMinutes(1),
        override val maxLifetime: Duration = Duration.ofMinutes(1),
    ) : DatabaseConfig {
        init {
            require(filePath.isNotBlank()) { "Json filePath must not be blank" }
        }
    }

    data class SQLite(
        val filePath: String,
        override val maxPoolSize: Int = 1,
        override val minIdle: Int = 1,
        override val connectionTimeout: Duration = Duration.ofSeconds(30),
        override val idleTimeout: Duration = Duration.ofMinutes(10),
        override val maxLifetime: Duration = Duration.ofMinutes(30),
    ) : DatabaseConfig {
        init {
            require(filePath.isNotBlank()) { "SQLite filePath must not be blank" }
        }
    }

    data class MySql(
        val host: String,
        val port: Int,
        val database: String,
        val username: String,
        val password: String,
        val parameters: Map<String, String> = mapOf("useSSL" to "false", "serverTimezone" to "UTC"),
        override val maxPoolSize: Int = 10,
        override val minIdle: Int = 1,
        override val connectionTimeout: Duration = Duration.ofSeconds(30),
        override val idleTimeout: Duration = Duration.ofMinutes(10),
        override val maxLifetime: Duration = Duration.ofMinutes(30),
    ) : DatabaseConfig {
        init {
            require(host.isNotBlank()) { "MySQL host must not be blank" }
            require(port in 1..65535) { "MySQL port must be in range 1..65535" }
            require(database.isNotBlank()) { "MySQL database must not be blank" }
            require(username.isNotBlank()) { "MySQL username must not be blank" }
        }
    }

    data class PostgreSql(
        val host: String,
        val port: Int,
        val database: String,
        val username: String,
        val password: String,
        val parameters: Map<String, String> = emptyMap(),
        override val maxPoolSize: Int = 10,
        override val minIdle: Int = 1,
        override val connectionTimeout: Duration = Duration.ofSeconds(30),
        override val idleTimeout: Duration = Duration.ofMinutes(10),
        override val maxLifetime: Duration = Duration.ofMinutes(30),
    ) : DatabaseConfig {
        init {
            require(host.isNotBlank()) { "PostgreSQL host must not be blank" }
            require(port in 1..65535) { "PostgreSQL port must be in range 1..65535" }
            require(database.isNotBlank()) { "PostgreSQL database must not be blank" }
            require(username.isNotBlank()) { "PostgreSQL username must not be blank" }
        }
    }
}
