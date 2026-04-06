package studio.singlethread.lib.framework.api.scheduler

import java.time.Duration
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

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

fun interface STTask {
    fun run()
}

fun interface STFailureTask {
    fun run(error: Throwable?)
}

fun interface STCompletionTask {
    fun run(result: ScheduledExecutionResult)
}

fun interface STRepeatTask {
    fun run(context: ScheduledRepeatContext)
}

class ScheduledRepeatContext private constructor(
    val iteration: Long,
    private val control: ScheduledRepeatControl,
) {
    fun stop() {
        control.stop()
    }

    companion object {
        internal fun create(
            iteration: Long,
            control: ScheduledRepeatControl,
        ): ScheduledRepeatContext {
            return ScheduledRepeatContext(
                iteration = iteration,
                control = control,
            )
        }
    }
}

fun interface ScheduledCompletionListener {
    fun onComplete(result: ScheduledExecutionResult)
}

interface ChainedScheduledTask : ScheduledTask {
    fun onComplete(listener: ScheduledCompletionListener): ChainedScheduledTask

    fun onCompleteSync(listener: ScheduledCompletionListener): ChainedScheduledTask

    fun onCompleteAsync(listener: ScheduledCompletionListener): ChainedScheduledTask
}

interface ScheduledChain : ChainedScheduledTask {
    override fun onComplete(listener: ScheduledCompletionListener): ScheduledChain

    override fun onCompleteSync(listener: ScheduledCompletionListener): ScheduledChain

    override fun onCompleteAsync(listener: ScheduledCompletionListener): ScheduledChain

    fun onComplete(listener: STCompletionTask): ScheduledChain

    fun onCompleteSync(listener: STCompletionTask): ScheduledChain

    fun onCompleteAsync(listener: STCompletionTask): ScheduledChain

    fun onSuccess(listener: STTask): ScheduledChain

    fun onFailure(listener: STFailureTask): ScheduledChain

    fun thenSync(task: STTask): ScheduledChain

    fun thenAsync(task: STTask): ScheduledChain

    fun then(task: STTask): ScheduledChain

    fun thenDelay(
        duration: Duration,
        thread: ScheduleThread = ScheduleThread.SYNC,
        task: STTask,
    ): ScheduledChain
}

interface SchedulerService {
    fun runSync(task: Runnable): ScheduledTask

    fun runAsync(task: Runnable): ScheduledTask

    fun runLater(delayTicks: Long, task: Runnable): ScheduledTask

    fun runTimer(delayTicks: Long, periodTicks: Long, task: Runnable): ScheduledTask

    fun runDelayed(schedule: DelaySchedule, task: Runnable): ChainedScheduledTask

    fun runRepeating(schedule: RepeatSchedule, task: Runnable): ChainedScheduledTask

    fun <T> callSync(task: Callable<T>): CompletableFuture<T>

    /**
     * High-level convenience API (Twilight-like ergonomics) without requiring extension imports.
     */
    fun sync(task: STTask): ScheduledTask {
        return runSync(Runnable { task.run() })
    }

    fun async(task: STTask): ScheduledTask {
        return runAsync(Runnable { task.run() })
    }

    fun delay(
        duration: Duration,
        thread: ScheduleThread = ScheduleThread.SYNC,
        task: STTask,
    ): ScheduledChain {
        val schedule =
            when (thread) {
                ScheduleThread.SYNC -> DelaySchedule.sync(duration)
                ScheduleThread.ASYNC -> DelaySchedule.async(duration)
            }
        return createScheduledChain(this, runDelayed(schedule, Runnable { task.run() }))
    }

    fun repeat(
        every: Duration,
        delay: Duration = Duration.ZERO,
        thread: ScheduleThread = ScheduleThread.SYNC,
        task: STTask,
    ): ScheduledChain {
        val schedule =
            when (thread) {
                ScheduleThread.SYNC -> RepeatSchedule.sync(delay, every)
                ScheduleThread.ASYNC -> RepeatSchedule.async(delay, every)
        }
        return createScheduledChain(this, runRepeating(schedule, Runnable { task.run() }))
    }

    fun repeat(
        every: Duration,
        delay: Duration = Duration.ZERO,
        thread: ScheduleThread = ScheduleThread.SYNC,
        task: STRepeatTask,
    ): ScheduledChain {
        val control = ScheduledRepeatControl()
        val iterationCounter = AtomicLong(0L)
        val schedule =
            when (thread) {
                ScheduleThread.SYNC -> RepeatSchedule.sync(delay, every)
                ScheduleThread.ASYNC -> RepeatSchedule.async(delay, every)
            }
        val delegate =
            runRepeating(
                schedule = schedule,
                task =
                    Runnable {
                        if (control.isStopped()) {
                            return@Runnable
                        }
                        val iteration = iterationCounter.incrementAndGet()
                        task.run(
                            ScheduledRepeatContext.create(
                                iteration = iteration,
                                control = control,
                            ),
                        )
                    },
            )
        val chain = createScheduledChain(this, delegate)
        control.bind(chain)
        return chain
    }

    fun repeat(
        times: Int,
        every: Duration,
        delay: Duration = Duration.ZERO,
        thread: ScheduleThread = ScheduleThread.SYNC,
        task: STTask,
    ): ScheduledChain {
        require(times > 0) { "times must be > 0" }
        return repeat(
            every = every,
            delay = delay,
            thread = thread,
            task =
                STRepeatTask { context ->
                    task.run()
                    if (context.iteration >= times.toLong()) {
                        context.stop()
                    }
                },
        )
    }
}

internal class ScheduledRepeatControl {
    private val stopped = AtomicBoolean(false)
    private val scheduledTaskRef = AtomicReference<ScheduledTask?>()

    fun stop() {
        if (!stopped.compareAndSet(false, true)) {
            return
        }
        scheduledTaskRef.get()?.cancel()
    }

    fun bind(scheduledTask: ScheduledTask) {
        if (!scheduledTaskRef.compareAndSet(null, scheduledTask)) {
            return
        }
        if (stopped.get()) {
            scheduledTask.cancel()
        }
    }

    fun isStopped(): Boolean {
        return stopped.get()
    }
}
