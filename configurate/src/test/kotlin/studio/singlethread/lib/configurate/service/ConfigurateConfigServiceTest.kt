package studio.singlethread.lib.configurate.service

import org.spongepowered.configurate.objectmapping.ConfigSerializable
import org.spongepowered.configurate.objectmapping.meta.Comment
import org.spongepowered.configurate.objectmapping.meta.Setting
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConfigurateConfigServiceTest {
    @Test
    fun `load creates file and writes defaults when config is missing`() {
        val service = ConfigurateConfigService()
        val tempDir = Files.createTempDirectory("stlib-configurate-test")
        val configPath = tempDir.resolve("config/example.yml")

        val loaded = service.load(configPath, ExampleConfig::class.java)

        assertTrue(Files.exists(configPath), "config file should be created")
        assertEquals("bar", loaded.foo)
        assertEquals(true, loaded.enabled)

        val raw = Files.readString(configPath)
        assertTrue(raw.contains("foo: bar"), "default foo should be materialized")
        assertTrue(raw.contains("enabled: true"), "default enabled should be materialized")
    }

    @Test
    fun `load rewrites existing uncommented file to materialize comments`() {
        val service = ConfigurateConfigService()
        val tempDir = Files.createTempDirectory("stlib-configurate-test-existing")
        val configPath = tempDir.resolve("config/example.yml")
        Files.createDirectories(configPath.parent)
        Files.writeString(
            configPath,
            """
            foo: custom
            enabled: false
            """.trimIndent(),
        )

        val loaded = service.load(configPath, CommentedExampleConfig::class.java)

        assertEquals("custom", loaded.foo)
        assertEquals(false, loaded.enabled)
        val raw = Files.readString(configPath)
        assertTrue(raw.contains("#"), "config should be rewritten with comments. raw=[$raw]")
        assertTrue(raw.contains("foo: custom"))
        assertTrue(raw.contains("enabled: false"))
    }

    @ConfigSerializable
    class ExampleConfig {
        var foo: String = "bar"
        var enabled: Boolean = true
    }

    @ConfigSerializable
    class CommentedExampleConfig {
        @field:Setting("foo")
        @field:Comment("Sample string option.")
        var foo: String = "bar"

        @field:Setting("enabled")
        @field:Comment("Sample boolean option.")
        var enabled: Boolean = true
    }
}
