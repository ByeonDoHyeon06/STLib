package studio.singlethread.lib.framework.bukkit.event

import org.bukkit.event.Listener
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BukkitEventRegistrarTest {
    @Test
    fun `listen should register each bukkit listener once`() {
        val registered = mutableListOf<Listener>()
        val unregistered = mutableListOf<Listener>()
        val registrar = BukkitEventRegistrar(registered::add, unregistered::add)
        val listener = BukkitOnlyListener()

        registrar.listen(listener)
        registrar.listen(listener)

        assertEquals(1, registered.size)
        assertEquals(0, unregistered.size)
    }

    @Test
    fun `unlisten should unregister tracked listener once`() {
        val registered = mutableListOf<Listener>()
        val unregistered = mutableListOf<Listener>()
        val registrar = BukkitEventRegistrar(registered::add, unregistered::add)
        val listener = BukkitOnlyListener()

        registrar.listen(listener)
        registrar.unlisten(listener)
        registrar.unlisten(listener)

        assertEquals(1, registered.size)
        assertEquals(1, unregistered.size)
    }

    @Test
    fun `unlisten all should unregister every tracked listener`() {
        val registered = mutableListOf<Listener>()
        val unregistered = mutableListOf<Listener>()
        val registrar = BukkitEventRegistrar(registered::add, unregistered::add)
        val first = BukkitOnlyListener()
        val second = BukkitOnlyListener()

        registrar.listen(first)
        registrar.listen(second)
        registrar.unlistenAll()

        assertEquals(2, registered.size)
        assertEquals(2, unregistered.size)
    }

    private class BukkitOnlyListener : Listener
}
