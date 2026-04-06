package studio.singlethread.lib.framework.bukkit.version

import org.bukkit.Server
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class BukkitRuntimeResolverTest {
    @Test
    fun `resolver should detect paper from server metadata`() {
        val server = mock(Server::class.java)
        `when`(server.name).thenReturn("Paper")
        `when`(server.version).thenReturn("git-Paper-17 (MC: 1.20.1)")
        `when`(server.bukkitVersion).thenReturn("1.20.1-R0.1-SNAPSHOT")

        val resolved = BukkitRuntimeResolver.resolve(server)

        assertEquals(BukkitRuntime.PAPER, resolved.runtime)
    }

    @Test
    fun `resolver should detect folia from server metadata`() {
        val server = mock(Server::class.java)
        `when`(server.name).thenReturn("Folia")
        `when`(server.version).thenReturn("git-Folia-1")
        `when`(server.bukkitVersion).thenReturn("1.20.6-R0.1-SNAPSHOT")

        val resolved = BukkitRuntimeResolver.resolve(server)

        assertEquals(BukkitRuntime.FOLIA, resolved.runtime)
    }

    @Test
    fun `resolver should detect spigot from server metadata`() {
        val server = mock(Server::class.java)
        `when`(server.name).thenReturn("Spigot")
        `when`(server.version).thenReturn("git-Spigot-1")
        `when`(server.bukkitVersion).thenReturn("1.20.1-R0.1-SNAPSHOT")

        val resolved = BukkitRuntimeResolver.resolve(server)

        assertEquals(BukkitRuntime.SPIGOT, resolved.runtime)
    }
}
