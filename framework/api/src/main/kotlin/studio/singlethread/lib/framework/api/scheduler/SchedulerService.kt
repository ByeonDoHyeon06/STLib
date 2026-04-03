package studio.singlethread.lib.framework.api.scheduler

import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture

fun interface ScheduledTask {
    fun cancel()
}

interface SchedulerService {
    fun runSync(task: Runnable): ScheduledTask

    fun runAsync(task: Runnable): ScheduledTask

    fun runLater(delayTicks: Long, task: Runnable): ScheduledTask

    fun runTimer(delayTicks: Long, periodTicks: Long, task: Runnable): ScheduledTask

    fun <T> callSync(task: Callable<T>): CompletableFuture<T>
}
