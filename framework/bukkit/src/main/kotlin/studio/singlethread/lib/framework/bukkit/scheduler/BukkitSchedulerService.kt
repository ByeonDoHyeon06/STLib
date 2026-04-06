package studio.singlethread.lib.framework.bukkit.scheduler

import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import studio.singlethread.lib.framework.api.scheduler.ChainedScheduledTask
import studio.singlethread.lib.framework.api.scheduler.DelaySchedule
import studio.singlethread.lib.framework.api.scheduler.RepeatSchedule
import studio.singlethread.lib.framework.api.scheduler.ScheduleThread
import studio.singlethread.lib.framework.api.scheduler.ScheduledExecutionStatus
import studio.singlethread.lib.framework.api.scheduler.ScheduledTask
import studio.singlethread.lib.framework.api.scheduler.SchedulerService
import studio.singlethread.lib.framework.bukkit.scheduler.internal.CompletionAwareScheduledTask
import studio.singlethread.lib.framework.bukkit.scheduler.internal.FoliaSchedulerBridge
import studio.singlethread.lib.framework.bukkit.support.STCallbackFailureLogger
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

class BukkitSchedulerService(
    private val plugin: JavaPlugin,
    private val debugLoggingEnabled: () -> Boolean = { false },
) : SchedulerService {
    private val foliaBridge = FoliaSchedulerBridge.create(plugin)

    override fun runSync(task: Runnable): ScheduledTask {
        val foliaTask = foliaBridge?.runSync(task)
        if (foliaTask != null) {
            return foliaTask
        }

        val bukkitTask = Bukkit.getScheduler().runTask(plugin, task)
        return ScheduledTask { bukkitTask.cancel() }
    }

    override fun runAsync(task: Runnable): ScheduledTask {
        val foliaTask = foliaBridge?.runAsync(task)
        if (foliaTask != null) {
            return foliaTask
        }

        val bukkitTask = Bukkit.getScheduler().runTaskAsynchronously(plugin, task)
        return ScheduledTask { bukkitTask.cancel() }
    }

    override fun runLater(
        delayTicks: Long,
        task: Runnable,
    ): ScheduledTask {
        val normalizedDelay = delayTicks.coerceAtLeast(0L)
        return scheduleSyncLater(normalizedDelay, task)
    }

    override fun runTimer(
        delayTicks: Long,
        periodTicks: Long,
        task: Runnable,
    ): ScheduledTask {
        val normalizedDelay = delayTicks.coerceAtLeast(0L)
        val normalizedPeriod = periodTicks.coerceAtLeast(1L)
        return scheduleSyncTimer(normalizedDelay, normalizedPeriod, task)
    }

    override fun runDelayed(
        schedule: DelaySchedule,
        task: Runnable,
    ): ChainedScheduledTask {
        val delayTicks = toTicks(schedule.delay, schedule.unit, minimumTicks = 0L)
        val completionTask = completionTask()
        val wrapped =
            Runnable {
                if (completionTask.isCompleted()) {
                    return@Runnable
                }

                runCatching { task.run() }
                    .onSuccess {
                        completionTask.complete(ScheduledExecutionStatus.SUCCESS)
                    }.onFailure { error ->
                        completionTask.complete(ScheduledExecutionStatus.FAILED, error)
                        throw error
                    }
            }

        val delegate =
            when (schedule.thread) {
                ScheduleThread.SYNC -> scheduleSyncLater(delayTicks, wrapped)
                ScheduleThread.ASYNC -> scheduleAsyncLater(delayTicks, wrapped)
            }

        completionTask.attach(delegate)
        return completionTask
    }

    override fun runRepeating(
        schedule: RepeatSchedule,
        task: Runnable,
    ): ChainedScheduledTask {
        val delayTicks = toTicks(schedule.delay, schedule.unit, minimumTicks = 0L)
        val periodTicks = toTicks(schedule.period, schedule.unit, minimumTicks = 1L)
        val completionTask = completionTask()
        val wrapped =
            Runnable {
                if (completionTask.isCompleted()) {
                    return@Runnable
                }

                runCatching { task.run() }
                    .onFailure { error ->
                        completionTask.fail(error)
                        throw error
                    }
            }

        val delegate =
            when (schedule.thread) {
                ScheduleThread.SYNC -> scheduleSyncTimer(delayTicks, periodTicks, wrapped)
                ScheduleThread.ASYNC -> scheduleAsyncTimer(delayTicks, periodTicks, wrapped)
            }

        completionTask.attach(delegate)
        return completionTask
    }

    override fun <T> callSync(task: Callable<T>): CompletableFuture<T> {
        val future = CompletableFuture<T>()
        runSync(
            Runnable {
                runCatching { task.call() }
                    .onSuccess { value -> future.complete(value) }
                    .onFailure { error -> future.completeExceptionally(error) }
            },
        )
        return future
    }

    private fun completionTask(): CompletionAwareScheduledTask {
        return CompletionAwareScheduledTask(
            dispatchSync = { runnable -> runSync(runnable) },
            dispatchAsync = { runnable -> runAsync(runnable) },
            callbackFailureReporter = { phase, error ->
                STCallbackFailureLogger.log(
                    logger = plugin.logger,
                    subsystem = "Scheduler",
                    phase = phase,
                    error = error,
                    debugEnabled = debugLoggingEnabled,
                )
            },
        )
    }

    private fun scheduleSyncLater(
        delayTicks: Long,
        task: Runnable,
    ): ScheduledTask {
        val foliaTask = foliaBridge?.runLater(delayTicks, task)
        if (foliaTask != null) {
            return foliaTask
        }

        val bukkitTask = Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks)
        return ScheduledTask { bukkitTask.cancel() }
    }

    private fun scheduleSyncTimer(
        delayTicks: Long,
        periodTicks: Long,
        task: Runnable,
    ): ScheduledTask {
        val foliaTask = foliaBridge?.runTimer(delayTicks, periodTicks, task)
        if (foliaTask != null) {
            return foliaTask
        }

        val bukkitTask = Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks)
        return ScheduledTask { bukkitTask.cancel() }
    }

    private fun scheduleAsyncLater(
        delayTicks: Long,
        task: Runnable,
    ): ScheduledTask {
        val foliaTask = foliaBridge?.runAsyncLater(delayTicks, task)
        if (foliaTask != null) {
            return foliaTask
        }

        val bukkitTask = Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, delayTicks)
        return ScheduledTask { bukkitTask.cancel() }
    }

    private fun scheduleAsyncTimer(
        delayTicks: Long,
        periodTicks: Long,
        task: Runnable,
    ): ScheduledTask {
        val foliaTask = foliaBridge?.runAsyncTimer(delayTicks, periodTicks, task)
        if (foliaTask != null) {
            return foliaTask
        }

        val bukkitTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, delayTicks, periodTicks)
        return ScheduledTask { bukkitTask.cancel() }
    }

    private fun toTicks(
        amount: Long,
        unit: TimeUnit,
        minimumTicks: Long,
    ): Long {
        val normalizedAmount = amount.coerceAtLeast(0L)
        val millis = unit.toMillis(normalizedAmount)
        val ticks =
            if (millis <= 0L) {
                0L
            } else {
                ceil(millis / MILLIS_PER_TICK.toDouble()).toLong()
            }
        return ticks.coerceAtLeast(minimumTicks)
    }

    private companion object {
        private const val MILLIS_PER_TICK = 50L
    }
}
