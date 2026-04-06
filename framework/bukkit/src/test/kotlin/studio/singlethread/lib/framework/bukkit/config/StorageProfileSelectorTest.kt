package studio.singlethread.lib.framework.bukkit.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import studio.singlethread.lib.framework.core.capability.DefaultCapabilityRegistry
import studio.singlethread.lib.storage.api.config.DatabaseConfig
import java.nio.file.Path

class StorageProfileSelectorTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `selector should fallback to json when selected backend capability is disabled`() {
        val capabilities = DefaultCapabilityRegistry()
        capabilities.enable("storage:json")
        capabilities.disable("storage:mysql", "mysql disabled")

        val selector = StorageProfileSelector(tempDir.resolve("SamplePlugin"))
        val settings = StorageFileSettings().also {
            it.backend = StorageBackendType.MYSQL
            it.namespace = "sampleplugin"
        }

        val resolved = selector.resolve(settings, capabilities, pluginName = "SamplePlugin")
        assertEquals(DatabaseConfig.Json::class, resolved.databaseConfig::class)
    }

    @Test
    fun `selector should map sqlite backend when capability is enabled`() {
        val capabilities = DefaultCapabilityRegistry()
        capabilities.enable("storage:json")
        capabilities.enable("storage:sqlite")

        val selector = StorageProfileSelector(tempDir.resolve("SamplePlugin"))
        val settings = StorageFileSettings().also {
            it.backend = StorageBackendType.SQLITE
            it.namespace = "sampleplugin"
            it.sqlite.filePath = "data/sample.db"
        }

        val resolved = selector.resolve(settings, capabilities, pluginName = "SamplePlugin")
        assertEquals(DatabaseConfig.SQLite::class, resolved.databaseConfig::class)
    }
}

