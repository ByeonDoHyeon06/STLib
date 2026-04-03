package studio.singlethread.lib.health

import studio.singlethread.lib.dashboard.StlibDashboardHealthLevel
import java.time.Instant

interface StlibHealthSnapshotProvider {
    fun snapshot(): StlibHealthSnapshot
}

data class StlibHealthSnapshot(
    val generatedAt: Instant,
    val dashboardProfile: String,
    val dashboardAvailable: Boolean,
    val persistenceEnabled: Boolean,
    val persistenceActive: Boolean,
    val commandMetricsEnabled: Boolean,
    val plugins: List<StlibPluginHealthSnapshot>,
)

data class StlibPluginHealthSnapshot(
    val name: String,
    val version: String,
    val healthLevel: StlibDashboardHealthLevel,
    val healthIssueCount: Int,
    val capabilityEnabledCount: Int,
    val capabilityDisabledCount: Int,
)
