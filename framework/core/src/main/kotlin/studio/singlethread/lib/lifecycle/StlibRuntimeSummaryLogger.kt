package studio.singlethread.lib.lifecycle

import studio.singlethread.lib.framework.bukkit.management.STPluginSnapshot
import studio.singlethread.lib.framework.bukkit.management.STPluginStatus
import studio.singlethread.lib.framework.bukkit.version.BukkitRuntimeResolution
import studio.singlethread.lib.framework.bukkit.version.BukkitServerVersionResolution

class StlibRuntimeSummaryLogger(
    private val allPlugins: () -> List<STPluginSnapshot>,
    private val resolveVersion: () -> BukkitServerVersionResolution,
    private val resolveRuntime: () -> BukkitRuntimeResolution,
    private val logInfo: (String) -> Unit,
) {
    fun print() {
        val enabledPlugins = allPlugins().filter { it.status == STPluginStatus.ENABLED }
        val pluginLine = formatPluginLine(enabledPlugins)
        val serverVersion = resolveServerVersion()
        val runtime = resolveRuntime().runtime.name.lowercase()
        logInfo("")
        logInfo("Plugins(${enabledPlugins.size}) : $pluginLine")
        logInfo("Server  : $serverVersion")
        logInfo("Runtime : $runtime")
        logInfo("")
    }

    private fun resolveServerVersion(): String {
        val resolution = resolveVersion()
        return resolution.resolved?.toString()
            ?: resolution.candidates.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: "unknown"
    }

    private fun formatPluginLine(plugins: List<STPluginSnapshot>): String {
        if (plugins.isEmpty()) {
            return "(none)"
        }
        val labels = plugins.map { "${it.name}(${it.version})" }
        return if (labels.size <= MAX_PLUGIN_LABELS) {
            labels.joinToString(separator = ", ")
        } else {
            val preview = labels.take(MAX_PLUGIN_LABELS).joinToString(separator = ", ")
            "$preview, +${labels.size - MAX_PLUGIN_LABELS} more"
        }
    }
    private companion object {
        private const val MAX_PLUGIN_LABELS = 8
    }
}
