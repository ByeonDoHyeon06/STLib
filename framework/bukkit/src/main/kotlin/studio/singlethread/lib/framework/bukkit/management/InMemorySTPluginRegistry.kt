package studio.singlethread.lib.framework.bukkit.management

import studio.singlethread.lib.framework.api.capability.CapabilityState
import java.time.Clock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

class InMemorySTPluginRegistry(
    private val clock: Clock = Clock.systemUTC(),
) {
    private val snapshots = ConcurrentHashMap<String, STPluginSnapshot>()
    @Volatile
    private var commandMetricsEnabled: Boolean = false

    fun register(descriptor: STPluginDescriptor) {
        val now = clock.instant()
        snapshots[key(descriptor.name)] =
            STPluginSnapshot(
                name = descriptor.name,
                version = descriptor.version,
                mainClass = descriptor.mainClass,
                status = STPluginStatus.LOADED,
                loadedAt = now,
                enabledAt = null,
                disabledAt = null,
                enableCount = 0,
                disableCount = 0,
                registeredCommandCount = 0,
                executedCommandCount = 0,
                lastCommandAt = null,
                capabilityEnabledCount = 0,
                capabilityDisabledCount = 0,
                capabilityUpdatedAt = null,
            )
    }

    fun markEnabled(pluginName: String) {
        update(pluginName) { current ->
            if (current == null) {
                return@update null
            }

            current.copy(
                status = STPluginStatus.ENABLED,
                enabledAt = clock.instant(),
                disabledAt = null,
                enableCount = current.enableCount + 1,
            )
        }
    }

    fun markDisabled(pluginName: String) {
        update(pluginName) { current ->
            if (current == null) {
                return@update null
            }

            current.copy(
                status = STPluginStatus.DISABLED,
                disabledAt = clock.instant(),
                disableCount = current.disableCount + 1,
            )
        }
    }

    fun markCommandRegistered(pluginName: String) {
        if (!commandMetricsEnabled) {
            return
        }
        update(pluginName) { current ->
            if (current == null) {
                return@update null
            }

            current.copy(
                registeredCommandCount = current.registeredCommandCount + 1,
            )
        }
    }

    fun markCommandExecuted(
        pluginName: String,
        at: Instant = clock.instant(),
    ) {
        if (!commandMetricsEnabled) {
            return
        }
        update(pluginName) { current ->
            if (current == null) {
                return@update null
            }

            current.copy(
                executedCommandCount = current.executedCommandCount + 1,
                lastCommandAt = at,
            )
        }
    }

    fun syncCapabilitySummary(
        pluginName: String,
        capabilitySnapshot: Map<String, CapabilityState>,
        at: Instant = clock.instant(),
    ) {
        update(pluginName) { current ->
            if (current == null) {
                return@update null
            }

            val enabledCount = capabilitySnapshot.count { (_, state) -> state.enabled }
            val disabledCount = capabilitySnapshot.size - enabledCount

            current.copy(
                capabilityEnabledCount = enabledCount,
                capabilityDisabledCount = disabledCount,
                capabilityUpdatedAt = at,
            )
        }
    }

    fun find(pluginName: String): STPluginSnapshot? {
        return snapshots[key(pluginName)]
    }

    fun snapshot(): List<STPluginSnapshot> {
        return snapshots.values.sortedBy { it.name.lowercase() }
    }

    fun configureCommandMetrics(enabled: Boolean) {
        commandMetricsEnabled = enabled
    }

    fun isCommandMetricsEnabled(): Boolean {
        return commandMetricsEnabled
    }

    private fun update(
        pluginName: String,
        transform: (STPluginSnapshot?) -> STPluginSnapshot?,
    ) {
        val id = key(pluginName)
        snapshots.compute(id) { _, current ->
            transform(current)
        }
    }

    private fun key(name: String): String {
        return name.trim().lowercase()
    }
}
