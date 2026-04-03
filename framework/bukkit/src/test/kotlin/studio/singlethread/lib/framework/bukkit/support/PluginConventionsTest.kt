package studio.singlethread.lib.framework.bukkit.support

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class PluginConventionsTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `permission should prefix plugin name and normalize casing`() {
        val permission = PluginConventions.permission(pluginName = "STLib", node = "Command.Reload")
        assertEquals("stlib.command.reload", permission)
    }

    @Test
    fun `permission should not double prefix when plugin node is already prefixed`() {
        val permission = PluginConventions.permission(pluginName = "stlib", node = "stlib.command.reload")
        assertEquals("stlib.command.reload", permission)
    }

    @Test
    fun `config path should resolve under config folder with yml extension`() {
        val path = PluginConventions.configPath(tempDir, "storage")
        assertEquals(tempDir.resolve("config/storage.yml"), path)
    }

    @Test
    fun `config path should reject traversal`() {
        assertThrows(IllegalArgumentException::class.java) {
            PluginConventions.configPath(tempDir, "../outside")
        }
    }
}

