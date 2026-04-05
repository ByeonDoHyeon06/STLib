package studio.singlethread.lib.framework.bukkit.lifecycle.support

internal class PluginLoadRuntimeCoordinator(
    private val loadCommandApi: Step,
    private val registerCoreServices: Step,
    private val bootstrapKernel: Step,
    private val bootstrapComponentGraph: Step,
    private val refreshRuntimeLoggingSwitches: () -> Unit,
    private val syncCapabilitySummary: () -> Unit,
) {
    data class Step(
        val run: () -> Boolean,
        val onFailure: () -> Unit = { },
    )

    fun prepare(): Boolean {
        if (!execute(loadCommandApi)) {
            return false
        }
        if (!execute(registerCoreServices)) {
            return false
        }
        if (!execute(bootstrapKernel)) {
            return false
        }
        if (!execute(bootstrapComponentGraph)) {
            return false
        }
        refreshRuntimeLoggingSwitches()
        syncCapabilitySummary()
        return true
    }

    private fun execute(step: Step): Boolean {
        val success = step.run()
        if (success) {
            return true
        }
        step.onFailure()
        return false
    }
}
