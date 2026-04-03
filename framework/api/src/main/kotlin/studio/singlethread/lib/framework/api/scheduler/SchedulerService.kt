package studio.singlethread.lib.framework.api.scheduler

import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture

fun interface ScheduledTask {
    fun cancel()
}

enum class ScheduledExecutionStatus {
    SUCCESS,
    CANCELLED,
    FAILED,
}

data class ScheduledExecutionResult(
    val status: ScheduledExecutionStatus,
    val error: Throwable? = null,
)

fun interface ScheduledCompletionListener {
    fun onComplete(result: ScheduledExecutionResult)
}

interface ChainedScheduledTask : ScheduledTask {
    fun onComplete(listener: ScheduledCompletionListener): ChainedScheduledTask

    fun onCompleteSync(listener: ScheduledCompletionListener): ChainedScheduledTask

    fun onCompleteAsync(listener: ScheduledCompletionListener): ChainedScheduledTask
}

interface SchedulerService {
    fun runSync(task: Runnable): ScheduledTask

    fun runAsync(task: Runnable): ScheduledTask

    fun runLater(delayTicks: Long, task: Runnable): ScheduledTask

    fun runTimer(delayTicks: Long, periodTicks: Long, task: Runnable): ScheduledTask

    fun runDelayed(schedule: DelaySchedule, task: Runnable): ChainedScheduledTask

    fun runRepeating(schedule: RepeatSchedule, task: Runnable): ChainedScheduledTask

    fun <T> callSync(task: Callable<T>): CompletableFuture<T>
}
