package studio.singlethread.lib.framework.api.scheduler

import java.time.Duration
import java.util.concurrent.TimeUnit

enum class ScheduleThread {
    SYNC,
    ASYNC,
}

data class DelaySchedule(
    val delay: Long,
    val unit: TimeUnit,
    val thread: ScheduleThread = ScheduleThread.SYNC,
) {
    init {
        require(delay >= 0L) { "delay must be >= 0" }
    }

    companion object {
        fun sync(
            delay: Long,
            unit: TimeUnit,
        ): DelaySchedule {
            return DelaySchedule(delay = delay, unit = unit, thread = ScheduleThread.SYNC)
        }

        fun async(
            delay: Long,
            unit: TimeUnit,
        ): DelaySchedule {
            return DelaySchedule(delay = delay, unit = unit, thread = ScheduleThread.ASYNC)
        }

        fun sync(delay: Duration): DelaySchedule {
            return sync(delay.toMillis(), TimeUnit.MILLISECONDS)
        }

        fun async(delay: Duration): DelaySchedule {
            return async(delay.toMillis(), TimeUnit.MILLISECONDS)
        }
    }
}

data class RepeatSchedule(
    val delay: Long,
    val period: Long,
    val unit: TimeUnit,
    val thread: ScheduleThread = ScheduleThread.SYNC,
) {
    init {
        require(delay >= 0L) { "delay must be >= 0" }
        require(period > 0L) { "period must be > 0" }
    }

    companion object {
        fun sync(
            delay: Long,
            period: Long,
            unit: TimeUnit,
        ): RepeatSchedule {
            return RepeatSchedule(
                delay = delay,
                period = period,
                unit = unit,
                thread = ScheduleThread.SYNC,
            )
        }

        fun async(
            delay: Long,
            period: Long,
            unit: TimeUnit,
        ): RepeatSchedule {
            return RepeatSchedule(
                delay = delay,
                period = period,
                unit = unit,
                thread = ScheduleThread.ASYNC,
            )
        }

        fun sync(
            delay: Duration,
            period: Duration,
        ): RepeatSchedule {
            return sync(delay.toMillis(), period.toMillis(), TimeUnit.MILLISECONDS)
        }

        fun async(
            delay: Duration,
            period: Duration,
        ): RepeatSchedule {
            return async(delay.toMillis(), period.toMillis(), TimeUnit.MILLISECONDS)
        }
    }
}
