package studio.singlethread.lib.framework.bukkit.event

import org.bukkit.event.Event
import org.bukkit.plugin.java.JavaPlugin

class BukkitEventCaller(
    private val callEvent: (Event) -> Unit,
) {
    constructor(plugin: JavaPlugin) : this(
        callEvent = { event -> plugin.server.pluginManager.callEvent(event) },
    )

    fun <T : Event> fire(event: T): T {
        callEvent(event)
        return event
    }
}

