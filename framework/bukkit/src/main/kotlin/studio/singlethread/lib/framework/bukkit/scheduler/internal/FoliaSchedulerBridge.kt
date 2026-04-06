package studio.singlethread.lib.framework.bukkit.scheduler.internal

import org.bukkit.plugin.java.JavaPlugin
import studio.singlethread.lib.framework.api.scheduler.ScheduledTask
import java.lang.reflect.Method
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

internal class FoliaSchedulerBridge(
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
