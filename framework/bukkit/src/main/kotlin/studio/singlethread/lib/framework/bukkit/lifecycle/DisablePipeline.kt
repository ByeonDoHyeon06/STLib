package studio.singlethread.lib.framework.bukkit.lifecycle

internal object DisablePipeline {
    fun run(
        disableAction: () -> Unit,
        unlistenAllAction: () -> Unit,
        cleanupAction: () -> Unit,
        kernelShutdownAction: () -> Unit,
        commandApiShutdownAction: () -> Unit,
        onStepFailure: (step: String, error: Throwable) -> Unit = { _, _ -> },
    ) {
        runStep("disable", disableAction, onStepFailure)
        runStep("unlistenAll", unlistenAllAction, onStepFailure)
        runStep("cleanup", cleanupAction, onStepFailure)
        runStep("kernelShutdown", kernelShutdownAction, onStepFailure)
        runStep("commandApiShutdown", commandApiShutdownAction, onStepFailure)
    }

    private fun runStep(
        step: String,
        action: () -> Unit,
        onFailure: (step: String, error: Throwable) -> Unit,
    ) {
        runCatching(action).onFailure { error ->
            onFailure(step, error)
        }
    }
}
