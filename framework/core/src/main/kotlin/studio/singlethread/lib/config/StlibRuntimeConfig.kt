package studio.singlethread.lib.config

import org.spongepowered.configurate.objectmapping.ConfigSerializable
import studio.singlethread.lib.framework.api.config.VersionedConfig

@ConfigSerializable
class StlibRuntimeConfig : VersionedConfig {
    override var version: Int = 1

    var dashboard: StlibDashboardConfig = StlibDashboardConfig()
    var metrics: StlibMetricsConfig = StlibMetricsConfig()
}

@ConfigSerializable
class StlibDashboardConfig {
    /**
     * Master toggle for /stlibgui availability.
     */
    var enabled: Boolean = true

    /**
     * Dashboard profile. Current supported value:
     * - core_ops
     */
    var profile: String = "core_ops"

    /**
     * When false, dashboard still works with live session counters only.
     * Persisted totals (storage load/flush) are skipped.
     */
    var persistStats: Boolean = false

    /**
     * Storage flush interval for persisted stats.
     * Effective range is normalized at runtime.
     */
    var flushIntervalSeconds: Int = 30
}

@ConfigSerializable
class StlibMetricsConfig {
    var command: StlibCommandMetricsConfig = StlibCommandMetricsConfig()
}

@ConfigSerializable
class StlibCommandMetricsConfig {
    /**
     * Command registration/execution metrics instrumentation toggle.
     */
    var enabled: Boolean = false
}
