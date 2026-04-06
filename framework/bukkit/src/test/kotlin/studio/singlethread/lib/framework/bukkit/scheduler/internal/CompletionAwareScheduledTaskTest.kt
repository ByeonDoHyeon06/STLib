package studio.singlethread.lib.framework.bukkit.scheduler.internal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import studio.singlethread.lib.framework.api.scheduler.ScheduledCompletionListener
import studio.singlethread.lib.framework.api.scheduler.ScheduledExecutionStatus
import studio.singlethread.lib.framework.api.scheduler.ScheduledTask

class CompletionAwareScheduledTaskTest {
    @Test
    fun `completion callback failures should be reported`() {
        val failures = mutableListOf<String>()
        val task =
            CompletionAwareScheduledTask(
                dispatchSync = { ScheduledTask {} },
                dispatchAsync = { ScheduledTask {} },
                callbackFailureReporter = { phase, error ->
                    failures += "$phase:${error.message}"
                },
            )

        task.onComplete(
            ScheduledCompletionListener {
                error("boom")
            },
        )
        task.complete(ScheduledExecutionStatus.SUCCESS)

        assertEquals(listOf("completion:boom"), failures)
    }
}
