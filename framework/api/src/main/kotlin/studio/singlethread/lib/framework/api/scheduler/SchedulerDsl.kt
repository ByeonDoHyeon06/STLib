package studio.singlethread.lib.framework.api.scheduler

fun ChainedScheduledTask.asChain(scheduler: SchedulerService): ScheduledChain {
    return createScheduledChain(scheduler, this)
}

internal fun createScheduledChain(
    scheduler: SchedulerService,
    delegate: ChainedScheduledTask,
): ScheduledChain {
    return DefaultScheduledChain(
        scheduler = scheduler,
        delegate = delegate,
    )
}

private class DefaultScheduledChain(
    private val scheduler: SchedulerService,
    private val delegate: ChainedScheduledTask,
) : ScheduledChain {
    override fun cancel() {
        delegate.cancel()
    }

    override fun onComplete(listener: ScheduledCompletionListener): ScheduledChain {
        delegate.onComplete(listener)
        return this
    }

    override fun onCompleteSync(listener: ScheduledCompletionListener): ScheduledChain {
        delegate.onCompleteSync(listener)
        return this
    }

    override fun onCompleteAsync(listener: ScheduledCompletionListener): ScheduledChain {
        delegate.onCompleteAsync(listener)
        return this
    }

    override fun onComplete(listener: STCompletionTask): ScheduledChain {
        return onComplete(ScheduledCompletionListener { result -> listener.run(result) })
    }

    override fun onCompleteSync(listener: STCompletionTask): ScheduledChain {
        return onCompleteSync(ScheduledCompletionListener { result -> listener.run(result) })
    }

    override fun onCompleteAsync(listener: STCompletionTask): ScheduledChain {
        return onCompleteAsync(ScheduledCompletionListener { result -> listener.run(result) })
    }

    override fun onSuccess(listener: STTask): ScheduledChain {
        onComplete(
            ScheduledCompletionListener { result ->
                if (result.status == ScheduledExecutionStatus.SUCCESS) {
                    listener.run()
                }
            },
        )
        return this
    }

    override fun onFailure(listener: STFailureTask): ScheduledChain {
        onComplete(
            ScheduledCompletionListener { result ->
                if (result.status == ScheduledExecutionStatus.FAILED) {
                    listener.run(result.error)
                }
            },
        )
        return this
    }

    override fun thenSync(task: STTask): ScheduledChain {
        return onSuccess(STTask { scheduler.runSync(Runnable { task.run() }) })
    }

    override fun thenAsync(task: STTask): ScheduledChain {
        return onSuccess(STTask { scheduler.runAsync(Runnable { task.run() }) })
    }

    override fun then(task: STTask): ScheduledChain {
        return thenSync(task)
    }

    override fun thenDelay(
        duration: java.time.Duration,
        thread: ScheduleThread,
        task: STTask,
    ): ScheduledChain {
        return onSuccess(
            STTask {
                scheduler.delay(
                    duration = duration,
                    thread = thread,
                    task = task,
                )
            },
        )
    }
}
