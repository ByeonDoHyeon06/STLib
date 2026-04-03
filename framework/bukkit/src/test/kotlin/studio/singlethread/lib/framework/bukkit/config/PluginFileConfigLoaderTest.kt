package studio.singlethread.lib.framework.bukkit.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import studio.singlethread.lib.configurate.service.ConfigurateConfigService
import java.nio.file.Path

class PluginFileConfigLoaderTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `loader should auto create storage and depend config files with defaults`() {
        val dataDir = tempDir.resolve("SamplePlugin")
        val loader = PluginFileConfigLoader(
            configService = ConfigurateConfigService(),
            dataDirectory = dataDir,
            pluginName = "SamplePlugin",
            pluginVersion = "1.2.3",
        )

        val config = loader.load()

        assertTrue(dataDir.resolve("config/plugin.yml").toFile().exists())
        assertTrue(dataDir.resolve("config/storage.yml").toFile().exists())
        assertTrue(dataDir.resolve("config/depend.yml").toFile().exists())
        assertEquals("1.2.3", config.plugin.version)
        assertFalse(config.plugin.debug)
        assertEquals(StorageBackendType.JSON, config.storage.backend)
        assertEquals("sampleplugin", config.storage.namespace)
        assertTrue(config.dependencies.runtime.loadDatabaseDrivers)
        assertTrue(config.dependencies.integrations.nexo)
        assertTrue(config.dependencies.integrations.mmoItems)
        assertTrue(config.dependencies.integrations.ecoItems)
        assertTrue(config.dependencies.integrations.placeholderApi)
    }

    @Test
    fun `loader should preserve custom storage backend from storage yml`() {
        val dataDir = tempDir.resolve("CustomPlugin")
        val configService = ConfigurateConfigService()
        val storagePath = dataDir.resolve("config/storage.yml")

        val customStorage = StorageFileSettings().also {
            it.backend = StorageBackendType.SQLITE
            it.namespace = "custom_space"
            it.sqlite.filePath = "data/custom.db"
        }
        configService.save(storagePath, customStorage, StorageFileSettings::class.java)

        val loader = PluginFileConfigLoader(
            configService = configService,
            dataDirectory = dataDir,
            pluginName = "CustomPlugin",
            pluginVersion = "1.0.0",
        )
        val loaded = loader.load()

        assertEquals(StorageBackendType.SQLITE, loaded.storage.backend)
        assertEquals("custom_space", loaded.storage.namespace)
        assertEquals("data/custom.db", loaded.storage.sqlite.filePath)
    }

    @Test
    fun `loader should preserve plugin debug and version from plugin yml`() {
        val dataDir = tempDir.resolve("PluginConfig")
        val configService = ConfigurateConfigService()
        val pluginPath = dataDir.resolve("config/plugin.yml")

        val pluginSettings = PluginSettingsFile().also {
            it.debug = true
            it.version = "2.0.0"
        }
        configService.save(pluginPath, pluginSettings, PluginSettingsFile::class.java)

        val loader = PluginFileConfigLoader(
            configService = configService,
            dataDirectory = dataDir,
            pluginName = "PluginConfig",
            pluginVersion = "1.0.0",
        )
        val loaded = loader.load()

        assertTrue(loaded.plugin.debug)
        assertEquals("2.0.0", loaded.plugin.version)
    }
}
