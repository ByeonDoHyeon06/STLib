package studio.singlethread.lib.framework.bukkit.lifecycle

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import studio.singlethread.lib.framework.bukkit.lifecycle.support.PluginLoadRuntimeCoordinator

class STPluginLoadRuntimeCoordinatorTest {
    @Test
    fun `prepare should run all steps in order and finalize on success`() {
        val sequence = mutableListOf<String>()
        val coordinator =
            PluginLoadRuntimeCoordinator(
                loadCommandApi = PluginLoadRuntimeCoordinator.Step(run = { sequence += "load"; true }),
                registerCoreServices = PluginLoadRuntimeCoordinator.Step(run = { sequence += "services"; true }),
                bootstrapKernel = PluginLoadRuntimeCoordinator.Step(run = { sequence += "kernel"; true }),
                bootstrapComponentGraph = PluginLoadRuntimeCoordinator.Step(run = { sequence += "di"; true }),
                refreshRuntimeLoggingSwitches = { sequence += "refresh" },
                syncCapabilitySummary = { sequence += "sync" },
            )

        val prepared = coordinator.prepare()

        assertTrue(prepared)
        assertEquals(listOf("load", "services", "kernel", "di", "refresh", "sync"), sequence)
    }

    @Test
    fun `prepare should fail fast and invoke step failure hook`() {
        val sequence = mutableListOf<String>()
        val coordinator =
            PluginLoadRuntimeCoordinator(
                loadCommandApi = PluginLoadRuntimeCoordinator.Step(run = { sequence += "load"; true }),
                registerCoreServices =
                    PluginLoadRuntimeCoordinator.Step(
                        run = {
                            sequence += "services"
                            false
                        },
                        onFailure = { sequence += "services-failed" },
                    ),
                bootstrapKernel = PluginLoadRuntimeCoordinator.Step(run = { sequence += "kernel"; true }),
                bootstrapComponentGraph = PluginLoadRuntimeCoordinator.Step(run = { sequence += "di"; true }),
                refreshRuntimeLoggingSwitches = { sequence += "refresh" },
                syncCapabilitySummary = { sequence += "sync" },
            )

        val prepared = coordinator.prepare()

        assertFalse(prepared)
        assertEquals(listOf("load", "services", "services-failed"), sequence)
    }
}
