package studio.singlethread.lib.framework.bukkit.resource

import org.bukkit.Bukkit
import org.bukkit.event.Event
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.server.PluginDisableEvent
import org.bukkit.event.server.PluginEnableEvent
import org.bukkit.plugin.EventExecutor
import org.bukkit.plugin.java.JavaPlugin
import studio.singlethread.lib.framework.api.capability.CapabilityRegistry
import studio.singlethread.lib.registry.common.provider.ExternalResourceProvider
import studio.singlethread.lib.registry.common.provider.ResourceProvider

data class ResourceCapabilityBinding(
    val capability: String,
    val provider: ResourceProvider,
)

class BukkitResourceIntegrationRuntime(
    private val plugin: JavaPlugin,
    private val capabilityRegistry: CapabilityRegistry,
    private val bindings: List<ResourceCapabilityBinding>,
) : AutoCloseable {
    private val listeners = mutableListOf<Listener>()
    private var activated = false

    fun activate() {
        if (activated) {
            return
        }
        activated = true

        refreshAllCapabilities()
        registerPluginStateBridge()
        registerProviderLoadedEventBridge("ItemsAdder", "dev.lone.itemsadder.api.Events.ItemsAdderLoadDataEvent")
        registerProviderLoadedEventBridge("Nexo", "com.nexomc.nexo.api.events.NexoItemsLoadedEvent")
        registerProviderLoadedEventBridge("Oraxen", "io.th0rgal.oraxen.api.events.OraxenItemsLoadedEvent")
        refreshAllCapabilities()
    }

    fun shutdown() {
        listeners.forEach { listener ->
            runCatching { HandlerList.unregisterAll(listener) }
        }
        listeners.clear()
        activated = false
    }

    override fun close() {
        shutdown()
    }

    fun refreshAllCapabilities() {
        bindings.forEach { binding ->
            val provider = binding.provider
            if (provider is ExternalResourceProvider) {
                provider.refreshState()
            }
            syncCapability(binding)
        }
    }

    private fun registerPluginStateBridge() {
        val listener =
            object : Listener {
                @org.bukkit.event.EventHandler(priority = EventPriority.MONITOR)
                fun onPluginEnable(event: PluginEnableEvent) {
                    handleUpstreamPluginState(pluginName = event.plugin.name, enabled = true)
                }

                @org.bukkit.event.EventHandler(priority = EventPriority.MONITOR)
                fun onPluginDisable(event: PluginDisableEvent) {
                    handleUpstreamPluginState(pluginName = event.plugin.name, enabled = false)
                }
            }

        plugin.server.pluginManager.registerEvents(listener, plugin)
        listeners += listener
    }

    private fun registerProviderLoadedEventBridge(
        upstreamPluginName: String,
        eventClassName: String,
    ) {
        if (bindings.none { binding ->
                val provider = binding.provider as? ExternalResourceProvider ?: return@none false
                provider.upstreamPluginName.equals(upstreamPluginName, ignoreCase = true)
            }
        ) {
            return
        }

        val eventClass =
            runCatching {
                Class.forName(eventClassName).asSubclass(Event::class.java)
            }.getOrNull()
                ?: return

        val listener = object : Listener {}

        val executor =
            EventExecutor { _, _ ->
                runOnMainThread {
                    handleUpstreamDataLoaded(upstreamPluginName)
                }
            }

        runCatching {
            plugin.server.pluginManager.registerEvent(
                eventClass,
                listener,
                EventPriority.MONITOR,
                executor,
                plugin,
                true,
            )
        }.onSuccess {
            listeners += listener
        }
    }

    private fun handleUpstreamPluginState(
        pluginName: String,
        enabled: Boolean,
    ) {
        bindings.forEach { binding ->
            val provider = binding.provider as? ExternalResourceProvider ?: return@forEach
            if (!provider.upstreamPluginName.equals(pluginName, ignoreCase = true)) {
                return@forEach
            }

            if (enabled) {
                provider.onUpstreamPluginEnabled()
            } else {
                provider.onUpstreamPluginDisabled()
            }
            syncCapability(binding)
        }
    }

    private fun handleUpstreamDataLoaded(upstreamPluginName: String) {
        bindings.forEach { binding ->
            val provider = binding.provider as? ExternalResourceProvider ?: return@forEach
            if (!provider.upstreamPluginName.equals(upstreamPluginName, ignoreCase = true)) {
                return@forEach
            }

            provider.onUpstreamDataLoaded()
            syncCapability(binding)
        }
    }

    private fun syncCapability(binding: ResourceCapabilityBinding) {
        if (binding.provider.isAvailable()) {
            capabilityRegistry.enable(binding.capability)
            return
        }

        val reason =
            (binding.provider as? ExternalResourceProvider)
                ?.unavailableReason()
                ?.takeIf { it.isNotBlank() }
                ?: "${binding.provider.providerId} provider unavailable"
        capabilityRegistry.disable(binding.capability, reason)
    }

    private fun runOnMainThread(block: () -> Unit) {
        if (Bukkit.isPrimaryThread()) {
            block()
            return
        }
        plugin.server.scheduler.runTask(plugin, Runnable { block() })
    }
}
