package studio.singlethread.lib.dashboard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import studio.singlethread.lib.framework.bukkit.management.STPluginSnapshot
import studio.singlethread.lib.framework.bukkit.management.STPluginStatus
import java.time.Instant

class StlibDashboardServiceTest {
    @Test
    fun `bootstrap without persisted load should use runtime counters only`() {
        val store = newStore()
        store.save(
            mapOf(
                "alpha" to StlibPersistedPluginStats("Alpha", totalEnableCount = 5, totalDisableCount = 2, totalCommandExecuted = 10),
            ),
        )

        val service =
            StlibDashboardService(
                plugins = {
                    listOf(
                        snapshot(
                            name = "Alpha",
                            status = STPluginStatus.ENABLED,
                            enabledAt = Instant.parse("2026-04-02T09:30:00Z"),
                            enableCount = 1,
                            disableCount = 0,
                            executedCommandCount = 3,
                        ),
                    )
                },
                statsStore = store,
            )

        service.bootstrap(loadPersisted = false)
        val entry = service.entries().single()

        assertEquals(1, entry.totalEnableCount)
        assertEquals(0, entry.totalDisableCount)
        assertEquals(3, entry.totalCommandExecuted)
        assertEquals(StlibDashboardHealthLevel.HEALTHY, entry.healthLevel)
        assertEquals(0, entry.healthIssueCount)
    }

    @Test
    fun `entries should merge baseline totals with runtime counters`() {
        val store = newStore()
        store.save(
            mapOf(
                "alpha" to StlibPersistedPluginStats("Alpha", totalEnableCount = 5, totalDisableCount = 2, totalCommandExecuted = 10),
            ),
        )

        val now = Instant.parse("2026-04-02T10:00:00Z")
        val service =
            StlibDashboardService(
                plugins = {
                    listOf(
                        snapshot(
                            name = "Alpha",
                            status = STPluginStatus.ENABLED,
                            enabledAt = Instant.parse("2026-04-02T09:30:00Z"),
                            enableCount = 1,
                            disableCount = 0,
                            registeredCommandCount = 2,
                            executedCommandCount = 3,
                            capabilityEnabledCount = 4,
                            capabilityDisabledCount = 1,
                        ),
                    )
                },
                statsStore = store,
                now = { now },
            )

        service.bootstrap()
        val entry = service.entries().single()

        assertEquals("Alpha", entry.name)
        assertEquals(6, entry.totalEnableCount)
        assertEquals(2, entry.totalDisableCount)
        assertEquals(13, entry.totalCommandExecuted)
        assertEquals(2, entry.registeredCommandCount)
        assertEquals(3, entry.executedCommandCount)
        assertEquals(4, entry.capabilityEnabledCount)
        assertEquals(1, entry.capabilityDisabledCount)
        assertEquals(1_800L, entry.uptime?.seconds)
        assertEquals(StlibDashboardHealthLevel.DEGRADED, entry.healthLevel)
        assertEquals(1, entry.healthIssueCount)
    }

    @Test
    fun `flush should persist merged totals and keep unknown baseline entries`() {
        val store = newStore()
        store.save(
            mapOf(
                "alpha" to StlibPersistedPluginStats("Alpha", totalEnableCount = 2, totalDisableCount = 1, totalCommandExecuted = 7),
                "legacy" to StlibPersistedPluginStats("Legacy", totalEnableCount = 9, totalDisableCount = 4, totalCommandExecuted = 22),
            ),
        )

        val service =
            StlibDashboardService(
                plugins = {
                    listOf(
                        snapshot(
                            name = "Alpha",
                            status = STPluginStatus.DISABLED,
                            enabledAt = Instant.parse("2026-04-02T09:00:00Z"),
                            disabledAt = Instant.parse("2026-04-02T09:10:00Z"),
                            enableCount = 3,
                            disableCount = 2,
                            executedCommandCount = 5,
                        ),
                    )
                },
                statsStore = store,
            )
        service.bootstrap()
        service.flush()

        val saved = store.load()
        assertEquals(2, saved.size)
        assertEquals(5, saved.getValue("alpha").totalEnableCount)
        assertEquals(3, saved.getValue("alpha").totalDisableCount)
        assertEquals(12, saved.getValue("alpha").totalCommandExecuted)
        assertEquals(9, saved.getValue("legacy").totalEnableCount)
        assertEquals(4, saved.getValue("legacy").totalDisableCount)
        assertEquals(22, saved.getValue("legacy").totalCommandExecuted)
    }

    private fun newStore(): StlibStatsStore {
        return StlibStatsStore(
            collection = InMemoryCollectionStorage("stlib_dashboard_stats"),
            logWarning = {},
        )
    }

    private fun snapshot(
        name: String,
        status: STPluginStatus,
        enabledAt: Instant? = null,
        disabledAt: Instant? = null,
        enableCount: Int = 0,
        disableCount: Int = 0,
        registeredCommandCount: Int = 0,
        executedCommandCount: Int = 0,
        capabilityEnabledCount: Int = 0,
        capabilityDisabledCount: Int = 0,
    ): STPluginSnapshot {
        return STPluginSnapshot(
            name = name,
            version = "1.0.0",
            mainClass = "sample.$name",
            status = status,
            loadedAt = Instant.parse("2026-04-02T08:00:00Z"),
            enabledAt = enabledAt,
            disabledAt = disabledAt,
            enableCount = enableCount,
            disableCount = disableCount,
            registeredCommandCount = registeredCommandCount,
            executedCommandCount = executedCommandCount,
            lastCommandAt = Instant.parse("2026-04-02T09:59:00Z"),
            capabilityEnabledCount = capabilityEnabledCount,
            capabilityDisabledCount = capabilityDisabledCount,
            capabilityUpdatedAt = Instant.parse("2026-04-02T09:58:00Z"),
        )
    }
}
