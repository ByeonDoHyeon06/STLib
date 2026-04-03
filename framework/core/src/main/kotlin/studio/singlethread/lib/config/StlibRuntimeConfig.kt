package studio.singlethread.lib.config

import org.spongepowered.configurate.objectmapping.ConfigSerializable
import org.spongepowered.configurate.objectmapping.meta.Comment
import studio.singlethread.lib.framework.api.config.VersionedConfig

@ConfigSerializable
class StlibRuntimeConfig : VersionedConfig {
    @field:Comment("Schema version for config migration.")
    override var version: Int = 1

    @field:Comment("Dashboard (stlibgui) runtime options.")
    var dashboard: StlibDashboardConfig = StlibDashboardConfig()

    @field:Comment("Metrics instrumentation options.")
    var metrics: StlibMetricsConfig = StlibMetricsConfig()
}

@ConfigSerializable
class StlibDashboardConfig {
    /**
     * Master toggle for /stlibgui availability.
     */
    @field:Comment("Enable/disable /stlibgui availability.")
    var enabled: Boolean = true

    /**
     * Dashboard profile. Current supported value:
     * - core_ops
     */
    @field:Comment("Dashboard profile. Current: core_ops")
    var profile: String = "core_ops"

    /**
     * When false, dashboard still works with live session counters only.
     * Persisted totals (storage load/flush) are skipped.
     */
    @field:Comment("Persist dashboard stats to storage backend.")
    var persistStats: Boolean = false

    /**
     * Storage flush interval for persisted stats.
     * Effective range is normalized at runtime.
     */
    @field:Comment("Flush interval seconds for persisted dashboard stats.")
    var flushIntervalSeconds: Int = 30
}

@ConfigSerializable
class StlibMetricsConfig {
    @field:Comment("Command metrics options.")
    var command: StlibCommandMetricsConfig = StlibCommandMetricsConfig()
}

@ConfigSerializable
class StlibCommandMetricsConfig {
    /**
     * Command registration/execution metrics instrumentation toggle.
     */
    @field:Comment("Enable command registration/execution metrics collection.")
    var enabled: Boolean = false
}
