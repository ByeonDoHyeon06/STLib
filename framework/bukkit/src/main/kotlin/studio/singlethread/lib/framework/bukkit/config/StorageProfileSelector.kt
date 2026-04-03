package studio.singlethread.lib.framework.bukkit.config

import studio.singlethread.lib.framework.api.capability.CapabilityRegistry
import studio.singlethread.lib.storage.api.config.DatabaseConfig
import studio.singlethread.lib.storage.api.config.StorageConfig
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration

class StorageProfileSelector(
    private val dataDirectory: Path,
) {
    fun resolve(
        settings: StorageFileSettings,
        capabilities: CapabilityRegistry,
        pluginName: String,
    ): StorageConfig {
        val backend = resolveBackend(settings.backend, capabilities)
        return resolveForBackend(settings, backend, pluginName)
    }

    fun resolveBackend(
        requested: StorageBackendType,
        capabilities: CapabilityRegistry,
    ): StorageBackendType {
        if (requested == StorageBackendType.SQLITE && capabilities.isEnabled(CapabilityNames.STORAGE_SQLITE)) {
            return StorageBackendType.SQLITE
        }
        if (requested == StorageBackendType.MYSQL && capabilities.isEnabled(CapabilityNames.STORAGE_MYSQL)) {
            return StorageBackendType.MYSQL
        }
        if (requested == StorageBackendType.POSTGRESQL && capabilities.isEnabled(CapabilityNames.STORAGE_POSTGRESQL)) {
            return StorageBackendType.POSTGRESQL
        }
        return StorageBackendType.JSON
    }

    fun resolveForBackend(
        settings: StorageFileSettings,
        backend: StorageBackendType,
        pluginName: String,
    ): StorageConfig {
        val namespace = StorageNamespaceNormalizer.normalize(settings.namespace, pluginName)
        val timeout = Duration.ofSeconds(settings.syncTimeoutSeconds.coerceAtLeast(1))
        val executorThreads = settings.executorThreads.coerceAtLeast(1)

        val databaseConfig =
            when (backend) {
                StorageBackendType.JSON -> DatabaseConfig.Json(resolvePath(settings.json.filePath, "data/storage.json").toString())
                StorageBackendType.SQLITE -> DatabaseConfig.SQLite(resolvePath(settings.sqlite.filePath, "data/storage.db").toString())
                StorageBackendType.MYSQL -> {
                    DatabaseConfig.MySql(
                        host = settings.mysql.host.ifBlank { "127.0.0.1" },
                        port = settings.mysql.port.coerceIn(1, 65535),
                        database = settings.mysql.database.ifBlank { "minecraft" },
                        username = settings.mysql.username.ifBlank { "root" },
                        password = settings.mysql.password,
                        parameters = settings.mysql.parameters.toMap(),
                    )
                }

                StorageBackendType.POSTGRESQL -> {
                    DatabaseConfig.PostgreSql(
                        host = settings.postgresql.host.ifBlank { "127.0.0.1" },
                        port = settings.postgresql.port.coerceIn(1, 65535),
                        database = settings.postgresql.database.ifBlank { "minecraft" },
                        username = settings.postgresql.username.ifBlank { "postgres" },
                        password = settings.postgresql.password,
                        parameters = settings.postgresql.parameters.toMap(),
                    )
                }
            }

        return StorageConfig(
            namespace = namespace,
            databaseConfig = databaseConfig,
            syncTimeout = timeout,
            executorThreads = executorThreads,
        )
    }

    private fun resolvePath(value: String, defaultValue: String): Path {
        val raw = value.ifBlank { defaultValue }
        val path = Paths.get(raw)
        if (path.isAbsolute) {
            return path
        }
        return dataDirectory.resolve(path).normalize()
    }

    private object CapabilityNames {
        const val STORAGE_SQLITE = "storage:sqlite"
        const val STORAGE_MYSQL = "storage:mysql"
        const val STORAGE_POSTGRESQL = "storage:postgresql"
    }
}

