package studio.singlethread.lib.framework.bukkit.management

import studio.singlethread.lib.framework.api.capability.CapabilityState
import java.time.Instant

object STPlugins {
    private val registry = InMemorySTPluginRegistry()

    fun all(): List<STPluginSnapshot> {
        return registry.snapshot()
    }

    fun find(name: String): STPluginSnapshot? {
        return registry.find(name)
    }

    fun configureCommandMetrics(enabled: Boolean) {
        registry.configureCommandMetrics(enabled)
    }

    fun isCommandMetricsEnabled(): Boolean {
        return registry.isCommandMetricsEnabled()
    }

    internal fun register(descriptor: STPluginDescriptor) {
        registry.register(descriptor)
    }

    internal fun markEnabled(pluginName: String) {
        registry.markEnabled(pluginName)
    }

    internal fun markDisabled(pluginName: String) {
        registry.markDisabled(pluginName)
    }

    internal fun markCommandRegistered(pluginName: String) {
        registry.markCommandRegistered(pluginName)
    }

    internal fun markCommandExecuted(
        pluginName: String,
        at: Instant,
    ) {
        registry.markCommandExecuted(pluginName, at)
    }

    internal fun syncCapabilitySummary(
        pluginName: String,
        capabilitySnapshot: Map<String, CapabilityState>,
        at: Instant,
    ) {
        registry.syncCapabilitySummary(pluginName, capabilitySnapshot, at)
    }
}
