package studio.singlethread.lib.health

import studio.singlethread.lib.dashboard.STLibDashboardService
import studio.singlethread.lib.dashboard.STLibDashboardRuntimeState
import studio.singlethread.lib.framework.api.bridge.BridgeMetricsSnapshot
import java.time.Instant

class STLibHealthSnapshotAssembler(
    private val dashboardService: STLibDashboardService,
    private val runtimeState: () -> STLibDashboardRuntimeState,
    private val dashboardProfile: () -> String,
    private val commandMetricsEnabled: () -> Boolean,
    private val schedulerEnabled: () -> Boolean,
    private val diDiscovered: () -> Int,
    private val diValidated: () -> Int,
    private val bridgeMode: () -> String,
    private val bridgeDistributed: () -> Boolean,
    private val bridgeRedisConnected: () -> Boolean,
    private val bridgeMetrics: () -> BridgeMetricsSnapshot,
    private val now: () -> Instant = { Instant.now() },
) : STLibHealthSnapshotProvider {
    override fun snapshot(): STLibHealthSnapshot {
        val state = runtimeState()
        val metrics = bridgeMetrics()
        return STLibHealthSnapshot(
            generatedAt = now(),
            dashboardProfile = dashboardProfile(),
            dashboardAvailable = state.available,
            persistenceEnabled = state.persistenceEnabled,
            persistenceActive = state.persistenceActive,
            commandMetricsEnabled = commandMetricsEnabled(),
            schedulerEnabled = schedulerEnabled(),
            diDiscovered = diDiscovered(),
            diValidated = diValidated(),
            bridgeMode = bridgeMode(),
            bridgeDistributed = bridgeDistributed(),
            bridgeRedisConnected = bridgeRedisConnected(),
            bridgePendingRequests = metrics.pendingRequests,
            bridgeRequestSubmitted = metrics.requestSubmitted,
            bridgeRequestTimedOut = metrics.requestTimedOut,
            bridgeRequestRejectedBackpressure = metrics.requestRejectedBackpressure,
            bridgeResponseLate = metrics.responseLate,
            bridgeResponseTargetMismatched = metrics.responseTargetMismatched,
            plugins =
                dashboardService.entries().map { entry ->
                    STLibPluginHealthSnapshot(
                        name = entry.name,
                        version = entry.version,
                        healthLevel = entry.healthLevel,
                        healthIssueCount = entry.healthIssueCount,
                        capabilityEnabledCount = entry.capabilityEnabledCount,
                        capabilityDisabledCount = entry.capabilityDisabledCount,
                    )
                },
        )
    }
}
