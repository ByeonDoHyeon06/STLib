package studio.singlethread.lib.framework.bukkit.config

import org.spongepowered.configurate.objectmapping.ConfigSerializable
import org.spongepowered.configurate.objectmapping.meta.Comment

@ConfigSerializable
class StorageFileSettings {
    @field:Comment("Default storage backend. Supported: JSON, SQLITE, MYSQL, POSTGRESQL.")
    var backend: StorageBackendType = StorageBackendType.JSON

    @field:Comment("Storage namespace. Blank means auto-generate from plugin name.")
    var namespace: String = ""

    @field:Comment("Max seconds allowed for sync operations (main thread safe-guard).")
    var syncTimeoutSeconds: Long = 5

    @field:Comment("Number of worker threads for async storage operations.")
    var executorThreads: Int = 4

    @field:Comment("JSON backend settings.")
    var json: JsonSettings = JsonSettings()

    @field:Comment("SQLite backend settings.")
    var sqlite: SqliteSettings = SqliteSettings()

    @field:Comment("MySQL backend settings.")
    var mysql: MySqlSettings = MySqlSettings()

    @field:Comment("PostgreSQL backend settings.")
    var postgresql: PostgreSqlSettings = PostgreSqlSettings()
}

@ConfigSerializable
class JsonSettings {
    @field:Comment("Path to JSON storage file, relative to plugin data folder.")
    var filePath: String = "data/storage.json"
}

@ConfigSerializable
class SqliteSettings {
    @field:Comment("Path to SQLite database file, relative to plugin data folder.")
    var filePath: String = "data/storage.db"
}

@ConfigSerializable
class MySqlSettings {
    @field:Comment("MySQL host.")
    var host: String = "127.0.0.1"

    @field:Comment("MySQL port.")
    var port: Int = 3306

    @field:Comment("MySQL database name.")
    var database: String = "minecraft"

    @field:Comment("MySQL username.")
    var username: String = "root"

    @field:Comment("MySQL password.")
    var password: String = "change-me"

    @field:Comment("JDBC query parameters appended to the connection URL.")
    var parameters: MutableMap<String, String> = linkedMapOf("useSSL" to "false", "serverTimezone" to "UTC")
}

@ConfigSerializable
class PostgreSqlSettings {
    @field:Comment("PostgreSQL host.")
    var host: String = "127.0.0.1"

    @field:Comment("PostgreSQL port.")
    var port: Int = 5432

    @field:Comment("PostgreSQL database name.")
    var database: String = "minecraft"

    @field:Comment("PostgreSQL username.")
    var username: String = "postgres"

    @field:Comment("PostgreSQL password.")
    var password: String = "change-me"

    @field:Comment("JDBC query parameters appended to the connection URL.")
    var parameters: MutableMap<String, String> = linkedMapOf()
}
