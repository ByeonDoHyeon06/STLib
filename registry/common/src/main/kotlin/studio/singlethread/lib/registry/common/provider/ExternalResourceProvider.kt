package studio.singlethread.lib.registry.common.provider

/**
 * Optional contract for providers backed by an external Bukkit plugin.
 *
 * Implementations may track extra readiness state (for example async item loading events)
 * and expose a concrete reason when unavailable.
 */
interface ExternalResourceProvider : ResourceProvider {
    /**
     * Upstream plugin name as exposed by Bukkit PluginManager.
     * Example: `ItemsAdder`, `Oraxen`, `Nexo`.
     */
    val upstreamPluginName: String

    /**
     * Refresh local availability state from current runtime/API conditions.
     */
    fun refreshState()

    /**
     * Called when the upstream plugin is enabled.
     */
    fun onUpstreamPluginEnabled() {
        refreshState()
    }

    /**
     * Called when the upstream plugin is disabled.
     */
    fun onUpstreamPluginDisabled() {
        refreshState()
    }

    /**
     * Called when the upstream plugin dispatches a "data loaded/reloaded" signal.
     */
    fun onUpstreamDataLoaded() {
        refreshState()
    }

    /**
     * Human-readable reason for unavailability.
     */
    fun unavailableReason(): String?
}
