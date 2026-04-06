package studio.singlethread.lib.framework.bukkit.scheduler.internal

import studio.singlethread.lib.framework.api.scheduler.ChainedScheduledTask
import studio.singlethread.lib.framework.api.scheduler.ScheduledCompletionListener
import studio.singlethread.lib.framework.api.scheduler.ScheduledExecutionResult
import studio.singlethread.lib.framework.api.scheduler.ScheduledExecutionStatus
import studio.singlethread.lib.framework.api.scheduler.ScheduledTask
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal class CompletionAwareScheduledTask(
    private val dispatchSync: (Runnable) -> ScheduledTask,
    private val dispatchAsync: (Runnable) -> ScheduledTask,
    private val callbackFailureReporter: (phase: String, error: Throwable) -> Unit = { _, _ -> },
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
        runCatching { callback(result) }
            .onFailure { error ->
                callbackFailureReporter("completion", error)
            }
    }
}
