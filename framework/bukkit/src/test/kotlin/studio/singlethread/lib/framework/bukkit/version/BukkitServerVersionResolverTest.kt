package studio.singlethread.lib.framework.bukkit.version

import org.bukkit.Server
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class BukkitServerVersionResolverTest {
    @Test
    fun `resolver should parse from bukkitVersion candidate`() {
        val server = mock(Server::class.java)
        `when`(server.bukkitVersion).thenReturn("1.20.1-R0.1-SNAPSHOT")
        `when`(server.version).thenReturn("git-Paper-17 (MC: 1.20.1)")

        val resolved = BukkitServerVersionResolver.resolve(server)

        assertEquals(MinecraftVersion(1, 20, 1), resolved.resolved)
    }

    @Test
    fun `resolver should include non blank candidates`() {
        val server = mock(Server::class.java)
        `when`(server.bukkitVersion).thenReturn("1.21-R0.1-SNAPSHOT")
        `when`(server.version).thenReturn("Paper")

        val resolved = BukkitServerVersionResolver.resolve(server)

        assertTrue(resolved.candidates.isNotEmpty())
    }
}

