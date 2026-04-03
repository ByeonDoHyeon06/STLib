package studio.singlethread.lib.health

import studio.singlethread.lib.dashboard.StlibDashboardService
import studio.singlethread.lib.dashboard.StlibDashboardRuntimeState
import java.time.Instant

class StlibHealthSnapshotAssembler(
    private val dashboardService: StlibDashboardService,
    private val runtimeState: () -> StlibDashboardRuntimeState,
    private val dashboardProfile: () -> String,
    private val commandMetricsEnabled: () -> Boolean,
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
