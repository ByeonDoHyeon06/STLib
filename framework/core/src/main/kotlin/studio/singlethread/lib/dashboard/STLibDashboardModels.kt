package studio.singlethread.lib.dashboard

import studio.singlethread.lib.framework.bukkit.management.STPluginStatus
import java.time.Duration
import java.time.Instant

enum class STLibDashboardHealthLevel {
    HEALTHY,
    DEGRADED,
}

data class STLibDashboardEntry(
    val name: String,
    val version: String,
    val mainClass: String,
    val status: STPluginStatus,
    val loadedAt: Instant,
    val enabledAt: Instant?,
    val disabledAt: Instant?,
    val uptime: Duration?,
    val capabilityEnabledCount: Int,
    val capabilityDisabledCount: Int,
    val capabilityUpdatedAt: Instant?,
    val registeredCommandCount: Int,
    val executedCommandCount: Int,
    val lastCommandAt: Instant?,
    val healthLevel: STLibDashboardHealthLevel,
    val healthIssueCount: Int,
    val lastLifecycleAt: Instant,
    val totalEnableCount: Long,
    val totalDisableCount: Long,
    val totalCommandExecuted: Long,
)

data class STLibPersistedPluginStats(
    val name: String,
    val totalEnableCount: Long,
    val totalDisableCount: Long,
    val totalCommandExecuted: Long,
)
