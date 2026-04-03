package studio.singlethread.lib.dashboard

import studio.singlethread.lib.framework.bukkit.management.STPluginSnapshot
import studio.singlethread.lib.framework.bukkit.management.STPluginStatus
import java.time.Duration
import java.time.Instant

class StlibDashboardService(
    private val plugins: () -> List<STPluginSnapshot>,
    private val statsStore: StlibStatsStore,
    private val now: () -> Instant = { Instant.now() },
) {
    private var baseline: Map<String, StlibPersistedPluginStats> = emptyMap()

    fun bootstrap(loadPersisted: Boolean = true) {
        baseline = if (loadPersisted) statsStore.load() else emptyMap()
    }

    fun entries(): List<StlibDashboardEntry> {
        val current = plugins()
        return current
            .map { snapshot -> toEntry(snapshot) }
            .sortedBy { it.name.lowercase() }
    }

    fun entry(pluginName: String): StlibDashboardEntry? {
        val key = StlibStatsStore.keyOf(pluginName)
        return entries().firstOrNull { StlibStatsStore.keyOf(it.name) == key }
    }

    fun flush() {
        val persisted = persistedSnapshot(plugins())
        statsStore.save(persisted)
    }

    private fun persistedSnapshot(current: List<STPluginSnapshot>): Map<String, StlibPersistedPluginStats> {
        val next = baseline.toMutableMap()
        current.forEach { snapshot ->
            val key = StlibStatsStore.keyOf(snapshot.name)
            val existing = baseline[key]
            next[key] =
                StlibPersistedPluginStats(
                    name = snapshot.name,
                    totalEnableCount = (existing?.totalEnableCount ?: 0L) + snapshot.enableCount.toLong(),
                    totalDisableCount = (existing?.totalDisableCount ?: 0L) + snapshot.disableCount.toLong(),
                    totalCommandExecuted = (existing?.totalCommandExecuted ?: 0L) + snapshot.executedCommandCount.toLong(),
                )
        }
        return next
    }

    private fun toEntry(snapshot: STPluginSnapshot): StlibDashboardEntry {
        val key = StlibStatsStore.keyOf(snapshot.name)
        val existing = baseline[key]
        val healthIssueCount = healthIssueCount(snapshot)
        return StlibDashboardEntry(
            name = snapshot.name,
            version = snapshot.version,
            mainClass = snapshot.mainClass,
            status = snapshot.status,
            loadedAt = snapshot.loadedAt,
            enabledAt = snapshot.enabledAt,
            disabledAt = snapshot.disabledAt,
            uptime = uptime(snapshot),
            capabilityEnabledCount = snapshot.capabilityEnabledCount,
            capabilityDisabledCount = snapshot.capabilityDisabledCount,
            capabilityUpdatedAt = snapshot.capabilityUpdatedAt,
            registeredCommandCount = snapshot.registeredCommandCount,
            executedCommandCount = snapshot.executedCommandCount,
            lastCommandAt = snapshot.lastCommandAt,
            healthLevel = if (healthIssueCount > 0) StlibDashboardHealthLevel.DEGRADED else StlibDashboardHealthLevel.HEALTHY,
            healthIssueCount = healthIssueCount,
            lastLifecycleAt = lastLifecycleAt(snapshot),
            totalEnableCount = (existing?.totalEnableCount ?: 0L) + snapshot.enableCount.toLong(),
            totalDisableCount = (existing?.totalDisableCount ?: 0L) + snapshot.disableCount.toLong(),
            totalCommandExecuted = (existing?.totalCommandExecuted ?: 0L) + snapshot.executedCommandCount.toLong(),
        )
    }

    private fun healthIssueCount(snapshot: STPluginSnapshot): Int {
        var issues = 0
        if (snapshot.status != STPluginStatus.ENABLED) {
            issues++
        }
        if (snapshot.status == STPluginStatus.ENABLED && snapshot.enabledAt == null) {
            issues++
        }
        if (snapshot.capabilityDisabledCount > 0) {
            issues++
        }
        return issues
    }

    private fun lastLifecycleAt(snapshot: STPluginSnapshot): Instant {
        return listOfNotNull(snapshot.loadedAt, snapshot.enabledAt, snapshot.disabledAt)
            .maxOrNull() ?: snapshot.loadedAt
    }

    private fun uptime(snapshot: STPluginSnapshot): Duration? {
        val enabledAt = snapshot.enabledAt ?: return null
        val end =
            when (snapshot.status) {
                STPluginStatus.ENABLED -> now()
                STPluginStatus.DISABLED -> snapshot.disabledAt ?: now()
                STPluginStatus.LOADED -> now()
            }
        if (end.isBefore(enabledAt)) {
            return Duration.ZERO
        }
        return Duration.between(enabledAt, end)
    }
}
