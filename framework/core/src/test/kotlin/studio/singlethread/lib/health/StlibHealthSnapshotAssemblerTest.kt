package studio.singlethread.lib.health

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import studio.singlethread.lib.dashboard.InMemoryCollectionStorage
import studio.singlethread.lib.dashboard.StlibDashboardRuntimeState
import studio.singlethread.lib.dashboard.StlibDashboardService
import studio.singlethread.lib.dashboard.StlibStatsStore
import studio.singlethread.lib.framework.bukkit.management.STPluginSnapshot
import studio.singlethread.lib.framework.bukkit.management.STPluginStatus
import java.time.Instant

class StlibHealthSnapshotAssemblerTest {
    @Test
    fun `snapshot should include runtime state and plugin health entries`() {
        val dashboardService =
            StlibDashboardService(
                plugins = {
                    listOf(
                        snapshot(
                            name = "Alpha",
                            status = STPluginStatus.ENABLED,
                            capabilityEnabledCount = 3,
                            capabilityDisabledCount = 0,
                        ),
                        snapshot(
                            name = "Beta",
                            status = STPluginStatus.DISABLED,
                            capabilityEnabledCount = 1,
                            capabilityDisabledCount = 1,
                        ),
                    )
                },
                statsStore =
                    StlibStatsStore(
                        collection = InMemoryCollectionStorage("stlib_dashboard_stats"),
                        logWarning = {},
                    ),
                now = { Instant.parse("2026-04-02T12:00:00Z") },
            )
        dashboardService.bootstrap(loadPersisted = false)

        val assembler =
            StlibHealthSnapshotAssembler(
                dashboardService = dashboardService,
                runtimeState = {
                    StlibDashboardRuntimeState(
                        available = true,
                        storageAvailable = false,
                        persistenceEnabled = true,
                        persistenceActive = false,
                    )
                },
                dashboardProfile = { "core_ops" },
                commandMetricsEnabled = { false },
                schedulerEnabled = { true },
                diDiscovered = { 6 },
                diValidated = { 6 },
                bridgeMode = { "composite" },
                bridgeDistributed = { true },
                bridgeRedisConnected = { true },
                now = { Instant.parse("2026-04-02T12:10:00Z") },
            )

        val snapshot = assembler.snapshot()

        assertEquals("core_ops", snapshot.dashboardProfile)
        assertEquals(true, snapshot.dashboardAvailable)
        assertEquals(true, snapshot.persistenceEnabled)
        assertEquals(false, snapshot.persistenceActive)
        assertEquals(false, snapshot.commandMetricsEnabled)
        assertEquals(true, snapshot.schedulerEnabled)
        assertEquals(6, snapshot.diDiscovered)
        assertEquals(6, snapshot.diValidated)
        assertEquals("composite", snapshot.bridgeMode)
        assertEquals(true, snapshot.bridgeDistributed)
        assertEquals(true, snapshot.bridgeRedisConnected)
        assertEquals(2, snapshot.plugins.size)
        assertEquals("Alpha", snapshot.plugins[0].name)
        assertEquals(0, snapshot.plugins[0].healthIssueCount)
        assertEquals("Beta", snapshot.plugins[1].name)
        assertEquals(2, snapshot.plugins[1].healthIssueCount)
    }

    private fun snapshot(
        name: String,
        status: STPluginStatus,
        capabilityEnabledCount: Int,
        capabilityDisabledCount: Int,
    ): STPluginSnapshot {
        return STPluginSnapshot(
            name = name,
            version = "1.0.0",
            mainClass = "sample.$name",
            status = status,
            loadedAt = Instant.parse("2026-04-02T08:00:00Z"),
            enabledAt = if (status == STPluginStatus.ENABLED) Instant.parse("2026-04-02T09:00:00Z") else null,
            disabledAt = if (status == STPluginStatus.DISABLED) Instant.parse("2026-04-02T10:00:00Z") else null,
            enableCount = 1,
            disableCount = if (status == STPluginStatus.DISABLED) 1 else 0,
            registeredCommandCount = 0,
            executedCommandCount = 0,
            lastCommandAt = null,
            capabilityEnabledCount = capabilityEnabledCount,
            capabilityDisabledCount = capabilityDisabledCount,
            capabilityUpdatedAt = Instant.parse("2026-04-02T11:00:00Z"),
        )
    }
}
