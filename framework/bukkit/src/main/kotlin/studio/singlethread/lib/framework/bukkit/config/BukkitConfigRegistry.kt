package studio.singlethread.lib.framework.bukkit.config

import studio.singlethread.lib.framework.api.config.ConfigMigrationPlan
import studio.singlethread.lib.framework.api.config.ConfigRegistry
import studio.singlethread.lib.framework.api.config.ConfigService
import studio.singlethread.lib.framework.api.config.ConfigMigrationResult
import studio.singlethread.lib.framework.api.config.VersionedConfig
import studio.singlethread.lib.framework.bukkit.support.PluginConventions
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.LinkedHashMap

class BukkitConfigRegistry(
    private val configService: ConfigService,
    private val dataDirectory: Path,
    private val logWarning: (String) -> Unit = {},
    private val now: () -> Instant = { Instant.now() },
) : ConfigRegistry {
    private val lock = Any()
    private val entries = LinkedHashMap<String, RegisteredConfig>()

    override fun <T : Any> register(fileName: String, type: Class<T>): T {
        synchronized(lock) {
            val id = id(fileName)
            val existing = entries[id]
            if (existing != null) {
                return castValue(existing, type, id)
            }

            val path = path(fileName)
            val loaded = configService.load(path, type)
            entries[id] =
                RegisteredConfig(
                    path = path,
                    type = type,
                    value = loaded,
                    migrationPlan = null,
                )
            return loaded
        }
    }

    override fun <T> register(
        fileName: String,
        type: Class<T>,
        migrationPlan: ConfigMigrationPlan<T>,
    ): T where T : Any, T : VersionedConfig {
        synchronized(lock) {
            val id = id(fileName)
            val existing = entries[id]
            if (existing != null) {
                return castValue(existing, type, id)
            }

            val path = path(fileName)
            val loaded = loadVersioned(path, type, migrationPlan)
            entries[id] =
                RegisteredConfig(
                    path = path,
                    type = type,
                    value = loaded,
                    migrationPlan = migrationPlan,
                )
            return loaded
        }
    }

    override fun <T : Any> current(fileName: String, type: Class<T>): T? {
        synchronized(lock) {
            val existing = entries[id(fileName)] ?: return null
            return castValue(existing, type, id(fileName))
        }
    }

    override fun <T : Any> reload(fileName: String, type: Class<T>): T {
        synchronized(lock) {
            val id = id(fileName)
            val path = path(fileName)
            val existing = entries[id]
            if (existing != null) {
                check(type == existing.type) {
                    "Config '$id' is registered as ${existing.type.name}, requested ${type.name}"
                }
            }

            val loaded =
                if (existing?.migrationPlan != null) {
                    @Suppress("UNCHECKED_CAST")
                    reloadVersioned(
                        path = path,
                        type = type as Class<VersionedConfig>,
                        migrationPlan = existing.migrationPlan as ConfigMigrationPlan<VersionedConfig>,
                    ) as T
                } else {
                    configService.reload(path, type)
                }

            entries[id] =
                RegisteredConfig(
                    path = path,
                    type = type,
                    value = loaded,
                    migrationPlan = existing?.migrationPlan,
                )
            return loaded
        }
    }

    override fun <T> reload(
        fileName: String,
        type: Class<T>,
        migrationPlan: ConfigMigrationPlan<T>,
    ): T where T : Any, T : VersionedConfig {
        synchronized(lock) {
            val id = id(fileName)
            val path = path(fileName)
            val loaded = reloadVersioned(path, type, migrationPlan)
            entries[id] =
                RegisteredConfig(
                    path = path,
                    type = type,
                    value = loaded,
                    migrationPlan = migrationPlan,
                )
            return loaded
        }
    }

    override fun <T : Any> save(fileName: String, value: T, type: Class<T>) {
        synchronized(lock) {
            val id = id(fileName)
            val path = path(fileName)
            val existing = entries[id]
            if (existing != null) {
                check(type == existing.type) {
                    "Config '$id' is registered as ${existing.type.name}, requested ${type.name}"
                }
            }
            configService.save(path, value, type)
            entries[id] =
                RegisteredConfig(
                    path = path,
                    type = type,
                    value = value,
                    migrationPlan = existing?.migrationPlan,
                )
        }
    }

    override fun reloadAll(): Map<String, Any> {
        synchronized(lock) {
            val refreshed = LinkedHashMap<String, Any>()
            entries.entries.toList().forEach { (id, entry) ->
                val reloaded = reloadEntry(entry)
                entries[id] = entry.copyWith(reloaded)
                refreshed[entry.path.toString()] = reloaded
            }
            return refreshed
        }
    }

    private fun path(fileName: String): Path {
        return PluginConventions.configPath(dataDirectory, fileName)
    }

    private fun id(fileName: String): String {
        return path(fileName).toString()
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> castValue(
        entry: RegisteredConfig,
        requestedType: Class<T>,
        id: String,
    ): T {
        check(requestedType == entry.type) {
            "Config '$id' is registered as ${entry.type.name}, requested ${requestedType.name}"
        }
        return entry.value as T
    }

    private fun reloadEntry(entry: RegisteredConfig): Any {
        @Suppress("UNCHECKED_CAST")
        val type = entry.type as Class<Any>
        if (entry.migrationPlan == null) {
            return configService.reload(entry.path, type)
        }

        @Suppress("UNCHECKED_CAST")
        return reloadVersioned(
            path = entry.path,
            type = type as Class<VersionedConfig>,
            migrationPlan = entry.migrationPlan as ConfigMigrationPlan<VersionedConfig>,
        )
    }

    private fun <T> loadVersioned(
        path: Path,
        type: Class<T>,
        migrationPlan: ConfigMigrationPlan<T>,
    ): T where T : Any, T : VersionedConfig {
        val existedBeforeLoad = Files.exists(path)
        val loaded = configService.load(path, type)
        return applyMigration(path, type, loaded, migrationPlan, existedBeforeLoad)
    }

    private fun <T> reloadVersioned(
        path: Path,
        type: Class<T>,
        migrationPlan: ConfigMigrationPlan<T>,
    ): T where T : Any, T : VersionedConfig {
        val existedBeforeReload = Files.exists(path)
        val loaded = configService.reload(path, type)
        return applyMigration(path, type, loaded, migrationPlan, existedBeforeReload)
    }

    private fun <T> applyMigration(
        path: Path,
        type: Class<T>,
        config: T,
        migrationPlan: ConfigMigrationPlan<T>,
        existedBeforeLoad: Boolean,
    ): T where T : Any, T : VersionedConfig {
        val migrationResult = migrationPlan.apply(config)
        if (!migrationResult.migrated) {
            return config
        }

        if (existedBeforeLoad) {
            backupBeforeMigration(path, migrationResult)
        }
        configService.save(path, config, type)
        return config
    }

    private fun backupBeforeMigration(
        path: Path,
        migrationResult: ConfigMigrationResult,
    ) {
        val parent = path.parent ?: return
        val backupDirectory = parent.resolve(".backup")
        val fileName = path.fileName?.toString() ?: "config.yml"
        val stamp = backupTimestamp.format(now())
        val backupPath =
            backupDirectory.resolve(
                "$fileName.v${migrationResult.fromVersion}-to-v${migrationResult.toVersion}.$stamp.bak",
            )

        runCatching {
            Files.createDirectories(backupDirectory)
            Files.copy(path, backupPath, StandardCopyOption.REPLACE_EXISTING)
        }.onFailure { error ->
            logWarning("Failed to backup config before migration (${path.fileName}): ${error.message}")
        }
    }

    private data class RegisteredConfig(
        val path: Path,
        val type: Class<*>,
        val value: Any,
        val migrationPlan: ConfigMigrationPlan<*>?,
    ) {
        fun copyWith(nextValue: Any): RegisteredConfig {
            return copy(value = nextValue)
        }
    }

    private companion object {
        private val backupTimestamp: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
                .withZone(ZoneOffset.UTC)
    }
}
