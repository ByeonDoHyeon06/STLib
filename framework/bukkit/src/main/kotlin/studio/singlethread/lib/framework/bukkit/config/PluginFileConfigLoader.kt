package studio.singlethread.lib.framework.bukkit.config

import studio.singlethread.lib.framework.api.config.ConfigService
import java.nio.file.Path

class PluginFileConfigLoader(
    private val configService: ConfigService,
    private val dataDirectory: Path,
    private val pluginName: String,
    private val pluginVersion: String,
) {
    fun load(): PluginFileConfig {
        val configDirectory = dataDirectory.resolve("config")
        val pluginPath = configDirectory.resolve("plugin.yml")
        val storagePath = configDirectory.resolve("storage.yml")
        val dependPath = configDirectory.resolve("depend.yml")

        val plugin = configService.load(pluginPath, PluginSettingsFile::class.java)
        val storage = configService.load(storagePath, StorageFileSettings::class.java)
        val dependencies = configService.load(dependPath, DependFileSettings::class.java)

        normalizePlugin(plugin)
        normalizeStorage(storage)

        configService.save(pluginPath, plugin, PluginSettingsFile::class.java)
        configService.save(storagePath, storage, StorageFileSettings::class.java)
        configService.save(dependPath, dependencies, DependFileSettings::class.java)

        return PluginFileConfig(plugin = plugin, storage = storage, dependencies = dependencies)
    }

    private fun normalizePlugin(plugin: PluginSettingsFile) {
        plugin.version = plugin.version.trim().ifBlank { pluginVersion }
    }

    private fun normalizeStorage(storage: StorageFileSettings) {
        storage.namespace = StorageNamespaceNormalizer.normalize(storage.namespace, pluginName)
        if (storage.syncTimeoutSeconds <= 0) {
            storage.syncTimeoutSeconds = 5
        }
        if (storage.executorThreads <= 0) {
            storage.executorThreads = 4
        }
    }
}
