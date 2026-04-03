package studio.singlethread.lib.framework.bukkit.version

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SupportedBukkitRuntimesTest {
    @Test
    fun `any policy should allow every runtime`() {
        val policy = SupportedBukkitRuntimes.any()
        assertTrue(policy.isSupported(BukkitRuntime.PAPER))
        assertTrue(policy.isSupported(BukkitRuntime.UNKNOWN))
    }

    @Test
    fun `only policy should allow declared runtime only`() {
        val policy = SupportedBukkitRuntimes.only(BukkitRuntime.PAPER, BukkitRuntime.FOLIA)
        assertTrue(policy.isSupported(BukkitRuntime.PAPER))
        assertTrue(policy.isSupported(BukkitRuntime.FOLIA))
        assertFalse(policy.isSupported(BukkitRuntime.SPIGOT))
    }
}
