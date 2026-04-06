package studio.singlethread.lib.dashboard

import studio.singlethread.lib.framework.api.scheduler.ScheduledTask

interface STLibDashboardRuntime {
    fun bootstrap(loadPersisted: Boolean)

    fun flush()
}

data class STLibDashboardRuntimeState(
    val available: Boolean,
    val storageAvailable: Boolean,
    val persistenceEnabled: Boolean,
    val persistenceActive: Boolean,
)

class STLibDashboardRuntimeAdapter(
    private val dashboardService: STLibDashboardService,
) : STLibDashboardRuntime {
    override fun bootstrap(loadPersisted: Boolean) {
        dashboardService.bootstrap(loadPersisted)
    }

    override fun flush() {
        dashboardService.flush()
    }
}

class STLibDashboardRuntimeController(
    private val dashboardService: STLibDashboardRuntime,
    private val capabilityPolicy: STLibStorageCapabilityPolicy,
    private val schedulePeriodicFlush: (delayTicks: Long, periodTicks: Long, task: Runnable) -> ScheduledTask,
    private val async: (task: Runnable) -> Unit,
    private val logWarning: (String) -> Unit,
    private val isDashboardEnabled: () -> Boolean = { true },
    private val isPersistenceEnabled: () -> Boolean = { true },
    private val flushPeriodTicks: () -> Long = { DEFAULT_FLUSH_PERIOD_TICKS },
) {
    private var flushTask: ScheduledTask? = null
    private var available: Boolean = false
    private var storageAvailable: Boolean = false
    private var persistenceEnabled: Boolean = false
    private var persistenceActive: Boolean = false

    fun initialize() {
        if (!isDashboardEnabled()) {
            available = false
            storageAvailable = false
            persistenceEnabled = false
            persistenceActive = false
            logWarning("STPlugin dashboard disabled by config/stlib.yml")
            return
        }

        storageAvailable = capabilityPolicy.isStorageAvailable()
        persistenceEnabled = isPersistenceEnabled()
        if (persistenceEnabled && !storageAvailable) {
            logWarning("STPlugin dashboard persistence disabled: storage capability is not enabled")
        }

        val loadPersisted = persistenceEnabled && storageAvailable

        available =
            runCatching {
                dashboardService.bootstrap(loadPersisted = loadPersisted)
                true
            }.onFailure { error ->
                logWarning("STPlugin dashboard bootstrap failed and was disabled: ${error.message}")
            }.getOrDefault(false)
        persistenceActive = available && loadPersisted
    }

    fun start() {
        if (!available) {
            logWarning("STPlugin dashboard is disabled")
            return
        }

        if (!persistenceActive) {
            return
        }

        flushTask?.cancel()
        flushTask =
            schedulePeriodicFlush(flushPeriodTicks(), flushPeriodTicks()) {
                async(Runnable {
                    flush("periodic")
                })
            }
    }

    fun stop() {
        flushTask?.cancel()
        flushTask = null
        if (!available) {
            return
        }
        if (!persistenceActive) {
            return
        }
        flush("disable")
    }

    fun isAvailable(): Boolean {
        return available
    }

    fun state(): STLibDashboardRuntimeState {
        return STLibDashboardRuntimeState(
            available = available,
            storageAvailable = storageAvailable,
            persistenceEnabled = persistenceEnabled,
            persistenceActive = persistenceActive,
        )
    }

    private fun flush(reason: String) {
        runCatching {
            dashboardService.flush()
        }.onFailure { error ->
            logWarning("Failed to flush STPlugin dashboard stats ($reason): ${error.message}")
        }
    }

    private companion object {
        private const val TICKS_PER_SECOND = 20L
        private const val DEFAULT_FLUSH_PERIOD_SECONDS = 30L
        private const val DEFAULT_FLUSH_PERIOD_TICKS = DEFAULT_FLUSH_PERIOD_SECONDS * TICKS_PER_SECOND
    }
}
