package studio.singlethread.lib.framework.api.scheduler

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import java.time.Duration
import java.util.concurrent.TimeUnit

class ScheduleSpecTest {
    @Test
    fun `delay schedule should reject negative delay`() {
        assertFailsWith<IllegalArgumentException> {
            DelaySchedule.sync(delay = -1, unit = TimeUnit.SECONDS)
        }
    }

    @Test
    fun `repeat schedule should reject non-positive period`() {
        assertFailsWith<IllegalArgumentException> {
            RepeatSchedule.sync(delay = 1, period = 0, unit = TimeUnit.SECONDS)
        }
    }

    @Test
    fun `duration factories should map to millisecond unit`() {
        val delay = DelaySchedule.async(Duration.ofSeconds(3))
        val repeat = RepeatSchedule.sync(Duration.ofSeconds(2), Duration.ofSeconds(5))

        assertEquals(3_000, delay.delay)
        assertEquals(TimeUnit.MILLISECONDS, delay.unit)
        assertEquals(ScheduleThread.ASYNC, delay.thread)

        assertEquals(2_000, repeat.delay)
        assertEquals(5_000, repeat.period)
        assertEquals(TimeUnit.MILLISECONDS, repeat.unit)
        assertEquals(ScheduleThread.SYNC, repeat.thread)
    }
}
