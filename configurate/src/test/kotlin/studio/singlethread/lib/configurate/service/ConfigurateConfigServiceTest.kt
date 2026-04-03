package studio.singlethread.lib.configurate.service

import org.spongepowered.configurate.objectmapping.ConfigSerializable
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

    @ConfigSerializable
    class ExampleConfig {
        var foo: String = "bar"
        var enabled: Boolean = true
    }
}

