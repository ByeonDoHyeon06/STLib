package studio.singlethread.lib.framework.bukkit.event

import org.bukkit.Bukkit
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin
import studio.singlethread.lib.framework.api.event.EventRegistrar
import java.util.Collections
import java.util.IdentityHashMap

class BukkitEventRegistrar(
    private val registerListener: (Listener) -> Unit,
    private val unregisterListener: (Listener) -> Unit,
) : EventRegistrar {
    private val listeners = Collections.newSetFromMap(IdentityHashMap<Listener, Boolean>())

    constructor(plugin: JavaPlugin) : this(
        registerListener = { listener -> Bukkit.getPluginManager().registerEvents(listener, plugin) },
        unregisterListener = { listener -> HandlerList.unregisterAll(listener) },
    )

    override fun listen(listener: Any) {
        listen(asBukkitListener(listener))
    }

    override fun unlisten(listener: Any) {
        unlisten(asBukkitListener(listener))
    }

    override fun unlistenAll() {
        val snapshot = synchronized(listeners) {
            val copy = listeners.toList()
            listeners.clear()
            copy
        }
        snapshot.forEach(unregisterListener)
    }

    fun listen(listener: Listener) {
        val shouldRegister = synchronized(listeners) { listeners.add(listener) }
        if (!shouldRegister) {
            return
        }
        registerListener(listener)
    }

    fun unlisten(listener: Listener) {
        val shouldUnregister = synchronized(listeners) { listeners.remove(listener) }
        if (!shouldUnregister) {
            return
        }
        unregisterListener(listener)
    }

    private fun asBukkitListener(listener: Any): Listener {
        return listener as? Listener
            ?: throw IllegalArgumentException(
                "Listener must implement org.bukkit.event.Listener on Bukkit platform: ${listener::class.qualifiedName}",
            )
    }
}
