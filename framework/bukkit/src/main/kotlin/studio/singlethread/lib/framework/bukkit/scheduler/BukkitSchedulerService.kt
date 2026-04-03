package studio.singlethread.lib.framework.bukkit.scheduler

import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import studio.singlethread.lib.framework.api.scheduler.ChainedScheduledTask
import studio.singlethread.lib.framework.api.scheduler.DelaySchedule
import studio.singlethread.lib.framework.api.scheduler.RepeatSchedule
import studio.singlethread.lib.framework.api.scheduler.ScheduleThread
import studio.singlethread.lib.framework.api.scheduler.ScheduledCompletionListener
import studio.singlethread.lib.framework.api.scheduler.ScheduledExecutionResult
import studio.singlethread.lib.framework.api.scheduler.ScheduledExecutionStatus
import studio.singlethread.lib.framework.api.scheduler.ScheduledTask
import studio.singlethread.lib.framework.api.scheduler.SchedulerService
import java.lang.reflect.Method
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer
import kotlin.math.ceil

class BukkitSchedulerService(
    private val plugin: JavaPlugin,
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

private class CompletionAwareScheduledTask(
    private val dispatchSync: (Runnable) -> ScheduledTask,
    private val dispatchAsync: (Runnable) -> ScheduledTask,
) : ChainedScheduledTask {
    private val delegate = AtomicReference<ScheduledTask?>()
    private val completed = AtomicBoolean(false)
    private val completionResult = AtomicReference<ScheduledExecutionResult?>()
    private val callbacks = CopyOnWriteArrayList<(ScheduledExecutionResult) -> Unit>()

    fun attach(task: ScheduledTask) {
        delegate.set(task)
    }

    fun complete(
        status: ScheduledExecutionStatus,
        error: Throwable? = null,
    ) {
        completeInternal(ScheduledExecutionResult(status = status, error = error))
    }

    fun fail(error: Throwable) {
        delegate.get()?.cancel()
        complete(status = ScheduledExecutionStatus.FAILED, error = error)
    }

    fun isCompleted(): Boolean {
        return completed.get()
    }

    override fun cancel() {
        delegate.get()?.cancel()
        complete(status = ScheduledExecutionStatus.CANCELLED)
    }

    override fun onComplete(listener: ScheduledCompletionListener): ChainedScheduledTask {
        registerCallback { result -> listener.onComplete(result) }
        return this
    }

    override fun onCompleteSync(listener: ScheduledCompletionListener): ChainedScheduledTask {
        registerCallback { result ->
            dispatchSync(
                Runnable {
                    listener.onComplete(result)
                },
            )
        }
        return this
    }

    override fun onCompleteAsync(listener: ScheduledCompletionListener): ChainedScheduledTask {
        registerCallback { result ->
            dispatchAsync(
                Runnable {
                    listener.onComplete(result)
                },
            )
        }
        return this
    }

    private fun registerCallback(callback: (ScheduledExecutionResult) -> Unit) {
        val alreadyCompleted = completionResult.get()
        if (alreadyCompleted != null) {
            safeInvoke(callback, alreadyCompleted)
            return
        }

        callbacks += callback

        val completedAfterRegistration = completionResult.get()
        if (completedAfterRegistration != null && callbacks.remove(callback)) {
            safeInvoke(callback, completedAfterRegistration)
        }
    }

    private fun completeInternal(result: ScheduledExecutionResult) {
        if (!completed.compareAndSet(false, true)) {
            return
        }

        completionResult.set(result)
        callbacks.toList().forEach { callback ->
            safeInvoke(callback, result)
        }
        callbacks.clear()
    }

    private fun safeInvoke(
        callback: (ScheduledExecutionResult) -> Unit,
        result: ScheduledExecutionResult,
    ) {
        runCatching {
            callback(result)
        }
    }
}

private class FoliaSchedulerBridge(
    private val plugin: JavaPlugin,
    private val globalScheduler: Any,
    private val globalRun: Method,
    private val globalRunDelayed: Method,
    private val globalRunAtFixedRate: Method,
    private val asyncScheduler: Any?,
    private val asyncRunNow: Method?,
    private val asyncRunDelayed: Method?,
    private val asyncRunAtFixedRate: Method?,
) {
    fun runSync(task: Runnable): ScheduledTask? {
        return invokeGlobal(globalRun, 0L, 0L, task)
    }

    fun runLater(
        delayTicks: Long,
        task: Runnable,
    ): ScheduledTask? {
        return invokeGlobal(globalRunDelayed, delayTicks, 0L, task)
    }

    fun runTimer(
        delayTicks: Long,
        periodTicks: Long,
        task: Runnable,
    ): ScheduledTask? {
        return invokeGlobal(globalRunAtFixedRate, delayTicks, periodTicks, task)
    }

    fun runAsync(task: Runnable): ScheduledTask? {
        val method = asyncRunNow ?: return null
        return invokeAsync(method = method, delayTicks = 0L, periodTicks = 0L, task = task)
    }

    fun runAsyncLater(
        delayTicks: Long,
        task: Runnable,
    ): ScheduledTask? {
        val method = asyncRunDelayed ?: return null
        return invokeAsync(method = method, delayTicks = delayTicks, periodTicks = 0L, task = task)
    }

    fun runAsyncTimer(
        delayTicks: Long,
        periodTicks: Long,
        task: Runnable,
    ): ScheduledTask? {
        val method = asyncRunAtFixedRate ?: return null
        return invokeAsync(method = method, delayTicks = delayTicks, periodTicks = periodTicks, task = task)
    }

    private fun invokeGlobal(
        method: Method,
        delayTicks: Long,
        periodTicks: Long,
        task: Runnable,
    ): ScheduledTask? {
        return runCatching {
            val consumer = Consumer<Any> { task.run() }
            val result =
                when (method.parameterCount) {
                    2 -> method.invoke(globalScheduler, plugin, consumer)
                    3 -> method.invoke(globalScheduler, plugin, consumer, delayTicks)
                    4 -> method.invoke(globalScheduler, plugin, consumer, delayTicks, periodTicks)
                    else -> return null
                }
            scheduledTask(result)
        }.getOrNull()
    }

    private fun invokeAsync(
        method: Method,
        delayTicks: Long,
        periodTicks: Long,
        task: Runnable,
    ): ScheduledTask? {
        val scheduler = asyncScheduler ?: return null
        return runCatching {
            val delayMillis = ticksToMillis(delayTicks)
            val periodMillis = ticksToMillis(periodTicks)
            val consumer = Consumer<Any> { task.run() }
            val result =
                when (method.parameterCount) {
                    2 -> method.invoke(scheduler, plugin, consumer)
                    4 -> method.invoke(scheduler, plugin, consumer, delayMillis, TimeUnit.MILLISECONDS)
                    5 -> method.invoke(scheduler, plugin, consumer, delayMillis, periodMillis, TimeUnit.MILLISECONDS)
                    else -> return null
                }
            scheduledTask(result)
        }.getOrNull()
    }

    private fun scheduledTask(taskObject: Any?): ScheduledTask {
        if (taskObject == null) {
            return ScheduledTask {}
        }

        val cancelMethod = taskObject.javaClass.methods.firstOrNull { it.name == "cancel" && it.parameterCount == 0 }
        if (cancelMethod == null) {
            return ScheduledTask {}
        }

        return ScheduledTask {
            runCatching { cancelMethod.invoke(taskObject) }
        }
    }

    private fun ticksToMillis(ticks: Long): Long {
        return ticks.coerceAtLeast(0L) * 50L
    }

    companion object {
        fun create(plugin: JavaPlugin): FoliaSchedulerBridge? {
            if (!isFoliaRuntime()) {
                return null
            }

            return runCatching {
                val server = plugin.server
                val globalScheduler = server.javaClass.getMethod("getGlobalRegionScheduler").invoke(server)
                val globalRun = findMethod(globalScheduler, "run", 2)
                val globalRunDelayed = findMethod(globalScheduler, "runDelayed", 3)
                val globalRunAtFixedRate = findMethod(globalScheduler, "runAtFixedRate", 4)

                val asyncScheduler =
                    runCatching {
                        server.javaClass.getMethod("getAsyncScheduler").invoke(server)
                    }.getOrNull()

                val asyncRunNow = asyncScheduler?.let { findMethodOrNull(it, "runNow", 2) }
                val asyncRunDelayed = asyncScheduler?.let { findMethodOrNull(it, "runDelayed", 4) }
                val asyncRunAtFixedRate = asyncScheduler?.let { findMethodOrNull(it, "runAtFixedRate", 5) }

                FoliaSchedulerBridge(
                    plugin = plugin,
                    globalScheduler = globalScheduler,
                    globalRun = globalRun,
                    globalRunDelayed = globalRunDelayed,
                    globalRunAtFixedRate = globalRunAtFixedRate,
                    asyncScheduler = asyncScheduler,
                    asyncRunNow = asyncRunNow,
                    asyncRunDelayed = asyncRunDelayed,
                    asyncRunAtFixedRate = asyncRunAtFixedRate,
                )
            }.getOrNull()
        }

        private fun findMethod(
            target: Any,
            name: String,
            parameterCount: Int,
        ): Method {
            return findMethodOrNull(target, name, parameterCount)
                ?: error("Unable to resolve method $name/$parameterCount on ${target.javaClass.name}")
        }

        private fun findMethodOrNull(
            target: Any,
            name: String,
            parameterCount: Int,
        ): Method? {
            return target.javaClass.methods.firstOrNull {
                it.name == name && it.parameterCount == parameterCount
            }
        }

        private fun isFoliaRuntime(): Boolean {
            return runCatching {
                Class.forName("io.papermc.paper.threadedregions.RegionizedServer")
            }.isSuccess
        }
    }
}
