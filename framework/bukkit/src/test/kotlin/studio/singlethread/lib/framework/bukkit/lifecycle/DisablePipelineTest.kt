package studio.singlethread.lib.framework.bukkit.lifecycle

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DisablePipelineTest {
    @Test
    fun `disable pipeline should run all steps even when disable step fails`() {
        val calls = mutableListOf<String>()

        DisablePipeline.run(
            disableAction = {
                calls += "disable"
                error("disable failed")
            },
            unlistenAllAction = { calls += "unlistenAll" },
            cleanupAction = { calls += "cleanup" },
            kernelShutdownAction = { calls += "kernelShutdown" },
            commandApiShutdownAction = { calls += "commandShutdown" },
        )

        assertEquals(
            listOf("disable", "unlistenAll", "cleanup", "kernelShutdown", "commandShutdown"),
            calls,
        )
    }

    @Test
    fun `disable pipeline should continue when unlisten step fails`() {
        val calls = mutableListOf<String>()

        DisablePipeline.run(
            disableAction = { calls += "disable" },
            unlistenAllAction = {
                calls += "unlistenAll"
                error("unlisten failed")
            },
            cleanupAction = { calls += "cleanup" },
            kernelShutdownAction = { calls += "kernelShutdown" },
            commandApiShutdownAction = { calls += "commandShutdown" },
        )

        assertEquals(
            listOf("disable", "unlistenAll", "cleanup", "kernelShutdown", "commandShutdown"),
            calls,
        )
    }

    @Test
    fun `disable pipeline should report failed steps`() {
        val failures = mutableListOf<String>()

        DisablePipeline.run(
            disableAction = { error("disable failed") },
            unlistenAllAction = {},
            cleanupAction = { error("cleanup failed") },
            kernelShutdownAction = {},
            commandApiShutdownAction = {},
            onStepFailure = { step, _ -> failures += step },
        )

        assertEquals(listOf("disable", "cleanup"), failures)
    }
}
