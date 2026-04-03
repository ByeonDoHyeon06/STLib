package studio.singlethread.lib.framework.bukkit.version

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SupportedServerVersionsTest {
    @Test
    fun `range policy should allow values inside inclusive bounds`() {
        val policy = SupportedServerVersions.range("1.19.4", "1.21.99")
        assertTrue(policy.isSupported(MinecraftVersion(1, 20, 1)))
    }

    @Test
    fun `range policy should reject values outside bounds`() {
        val policy = SupportedServerVersions.range("1.19.4", "1.21.99")
        assertFalse(policy.isSupported(MinecraftVersion(1, 18, 2)))
        assertFalse(policy.isSupported(MinecraftVersion(1, 22, 0)))
    }

    @Test
    fun `exact policy should only allow declared values`() {
        val policy = SupportedServerVersions.exact("1.20.4", "1.21.1")
        assertTrue(policy.isSupported(MinecraftVersion(1, 20, 4)))
        assertFalse(policy.isSupported(MinecraftVersion(1, 20, 5)))
    }
}

