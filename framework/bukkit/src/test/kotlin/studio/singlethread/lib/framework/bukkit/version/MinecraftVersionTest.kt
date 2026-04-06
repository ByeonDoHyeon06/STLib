package studio.singlethread.lib.framework.bukkit.version

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MinecraftVersionTest {
    @Test
    fun `parse should extract semantic version token`() {
        val parsed = MinecraftVersion.parse("git-Paper-17 (MC: 1.20.1)")
        assertEquals(MinecraftVersion(1, 20, 1), parsed)
    }

    @Test
    fun `parse should support major minor without patch`() {
        val parsed = MinecraftVersion.parse("1.21")
        assertEquals(MinecraftVersion(1, 21, 0), parsed)
    }

    @Test
    fun `parse should return null when token does not exist`() {
        assertNull(MinecraftVersion.parse("unknown"))
    }

    @Test
    fun `comparison should follow major minor patch order`() {
        val first = MinecraftVersion(1, 20, 4)
        val second = MinecraftVersion(1, 21, 0)
        assertTrue(first < second)
    }
}

