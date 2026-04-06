package studio.singlethread.lib.framework.bukkit.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.spongepowered.configurate.objectmapping.ConfigSerializable
import studio.singlethread.lib.configurate.service.ConfigurateConfigService
import studio.singlethread.lib.framework.api.config.VersionedConfig
import studio.singlethread.lib.framework.api.config.configMigrationPlan
import java.nio.file.Files
import java.nio.file.Path

class BukkitConfigRegistryTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `register should materialize config file and keep latest snapshot`() {
        val configService = ConfigurateConfigService()
        val registry = BukkitConfigRegistry(configService, tempDir)

        val loaded = registry.register("feature/example", ExampleConfig::class.java)

        assertEquals("default", loaded.message)
        assertNotNull(registry.current("feature/example", ExampleConfig::class.java))
        assertTrue(tempDir.resolve("config/feature/example.yml").toFile().exists())
    }

    @Test
    fun `reload all should refresh every registered config entry`() {
        val configService = ConfigurateConfigService()
        val registry = BukkitConfigRegistry(configService, tempDir)

        registry.register("a", ExampleConfig::class.java)
        registry.register("b", ExampleConfig::class.java)

        configService.save(
            tempDir.resolve("config/a.yml"),
            ExampleConfig().also { it.message = "updated-a" },
            ExampleConfig::class.java,
        )
        configService.save(
            tempDir.resolve("config/b.yml"),
            ExampleConfig().also { it.message = "updated-b" },
            ExampleConfig::class.java,
        )

        val snapshot = registry.reloadAll()

        assertEquals("updated-a", registry.current("a", ExampleConfig::class.java)?.message)
        assertEquals("updated-b", registry.current("b", ExampleConfig::class.java)?.message)
        assertEquals(setOf("a.yml", "b.yml"), snapshot.keys.map { Path.of(it).fileName.toString() }.toSet())
    }

    @Test
    fun `register with migration should upgrade versioned config and create backup`() {
        val configService = ConfigurateConfigService()
        val warnings = mutableListOf<String>()
        val registry =
            BukkitConfigRegistry(
                configService = configService,
                dataDirectory = tempDir,
                logWarning = { warnings += it },
                now = { java.time.Instant.parse("2026-04-02T12:00:00Z") },
            )

        val path = tempDir.resolve("config/versioned/example.yml")
        Files.createDirectories(path.parent)
        Files.writeString(
            path,
            """
            version: 1
            message: old
            enabled: true
            """.trimIndent(),
        )

        val migrationPlan =
            configMigrationPlan<VersionedExampleConfig>(latestVersion = 2) { migrations ->
                migrations.step(fromVersion = 1, toVersion = 2) { config ->
                    config.message = "migrated"
                    config.enabled = false
                }
            }

        val loaded = registry.register("versioned/example", VersionedExampleConfig::class.java, migrationPlan)

        assertEquals(2, loaded.version)
        assertEquals("migrated", loaded.message)
        assertEquals(false, loaded.enabled)

        val persisted = Files.readString(path)
        assertTrue(persisted.contains("version: 2"))
        assertTrue(persisted.contains("message: migrated"))

        val backupDir = path.parent.resolve(".backup")
        assertTrue(Files.exists(backupDir))
        val backups = Files.list(backupDir).use { stream -> stream.toList() }
        assertEquals(1, backups.size)
        assertTrue(backups.first().fileName.toString().contains("example.yml.v1-to-v2.20260402120000.bak"))
        assertEquals(emptyList<String>(), warnings)
    }

    @Test
    fun `reload all should preserve migration policy for versioned configs`() {
        val configService = ConfigurateConfigService()
        val registry = BukkitConfigRegistry(configService, tempDir)
        val path = tempDir.resolve("config/versioned/reload.yml")

        val migrationPlan =
            configMigrationPlan<VersionedExampleConfig>(latestVersion = 2) { migrations ->
                migrations.step(fromVersion = 1, toVersion = 2) { config ->
                    config.message = "upgraded"
                }
            }

        registry.register("versioned/reload", VersionedExampleConfig::class.java, migrationPlan)

        Files.writeString(
            path,
            """
            version: 1
            message: external-old
            enabled: true
            """.trimIndent(),
        )

        registry.reloadAll()

        val current = registry.current("versioned/reload", VersionedExampleConfig::class.java)
        assertEquals(2, current?.version)
        assertEquals("upgraded", current?.message)
    }

    @ConfigSerializable
    class ExampleConfig {
        var message: String = "default"
    }

    @ConfigSerializable
    class VersionedExampleConfig : VersionedConfig {
        override var version: Int = 1
        var message: String = "default"
        var enabled: Boolean = true
    }
}
