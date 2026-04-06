package studio.singlethread.lib.health

import studio.singlethread.lib.dashboard.STLibDashboardHealthLevel
import java.time.Instant

interface STLibHealthSnapshotProvider {
    fun snapshot(): STLibHealthSnapshot
}

data class STLibHealthSnapshot(
    val generatedAt: Instant,
    val dashboardProfile: String,
    val dashboardAvailable: Boolean,
    val persistenceEnabled: Boolean,
    val persistenceActive: Boolean,
    val commandMetricsEnabled: Boolean,
    val schedulerEnabled: Boolean,
    val diDiscovered: Int,
    val diValidated: Int,
    val bridgeMode: String,
    val bridgeDistributed: Boolean,
    val bridgeRedisConnected: Boolean,
    val bridgePendingRequests: Int,
    val bridgeRequestSubmitted: Long,
    val bridgeRequestTimedOut: Long,
    val bridgeRequestRejectedBackpressure: Long,
    val bridgeResponseLate: Long,
    val bridgeResponseTargetMismatched: Long,
    val plugins: List<STLibPluginHealthSnapshot>,
)

data class STLibPluginHealthSnapshot(
    val name: String,
    val version: String,
    val healthLevel: STLibDashboardHealthLevel,
    val healthIssueCount: Int,
    val capabilityEnabledCount: Int,
    val capabilityDisabledCount: Int,
)
