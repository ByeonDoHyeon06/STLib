package studio.singlethread.lib.framework.bukkit.config

import org.spongepowered.configurate.objectmapping.ConfigSerializable

@ConfigSerializable
class StorageFileSettings {
    var backend: StorageBackendType = StorageBackendType.JSON
    var namespace: String = ""
    var syncTimeoutSeconds: Long = 5
    var executorThreads: Int = 4
    var json: JsonSettings = JsonSettings()
    var sqlite: SqliteSettings = SqliteSettings()
    var mysql: MySqlSettings = MySqlSettings()
    var postgresql: PostgreSqlSettings = PostgreSqlSettings()
}

@ConfigSerializable
class JsonSettings {
    var filePath: String = "data/storage.json"
}

@ConfigSerializable
class SqliteSettings {
    var filePath: String = "data/storage.db"
}

@ConfigSerializable
class MySqlSettings {
    var host: String = "127.0.0.1"
    var port: Int = 3306
    var database: String = "minecraft"
    var username: String = "root"
    var password: String = "change-me"
    var parameters: MutableMap<String, String> = linkedMapOf("useSSL" to "false", "serverTimezone" to "UTC")
}

@ConfigSerializable
class PostgreSqlSettings {
    var host: String = "127.0.0.1"
    var port: Int = 5432
    var database: String = "minecraft"
    var username: String = "postgres"
    var password: String = "change-me"
    var parameters: MutableMap<String, String> = linkedMapOf()
}
