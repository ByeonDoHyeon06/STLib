package studio.singlethread.lib.health

import studio.singlethread.lib.dashboard.StlibDashboardService
import studio.singlethread.lib.dashboard.StlibDashboardRuntimeState
import java.time.Instant

class StlibHealthSnapshotAssembler(
    private val dashboardService: StlibDashboardService,
    private val runtimeState: () -> StlibDashboardRuntimeState,
    private val dashboardProfile: () -> String,
    private val commandMetricsEnabled: () -> Boolean,
    private val schedulerEnabled: () -> Boolean,
    private val diDiscovered: () -> Int,
    private val diValidated: () -> Int,
    private val bridgeMode: () -> String,
    private val bridgeDistributed: () -> Boolean,
    private val bridgeRedisConnected: () -> Boolean,
    private val now: () -> Instant = { Instant.now() },
) : StlibHealthSnapshotProvider {
    override fun snapshot(): StlibHealthSnapshot {
        val state = runtimeState()
        return StlibHealthSnapshot(
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
            plugins =
                dashboardService.entries().map { entry ->
                    StlibPluginHealthSnapshot(
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
