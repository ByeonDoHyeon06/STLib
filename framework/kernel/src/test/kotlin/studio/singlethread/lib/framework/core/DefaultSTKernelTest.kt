package studio.singlethread.lib.framework.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import studio.singlethread.lib.framework.api.capability.CapabilityRegistry
import studio.singlethread.lib.framework.api.text.TextService
import studio.singlethread.lib.framework.core.capability.DefaultCapabilityRegistry
import studio.singlethread.lib.framework.core.kernel.DefaultSTKernel
import studio.singlethread.lib.framework.core.text.MiniMessageTextService

class DefaultSTKernelTest {
    @Test
    fun `kernel should register and resolve services and capabilities`() {
        val kernel = DefaultSTKernel()

        val text = MiniMessageTextService()
        kernel.registerService(TextService::class, text)
        kernel.capabilityRegistry.enable("feature:test")

        assertEquals(text, kernel.service(TextService::class))
        assertTrue(kernel.capabilityRegistry.isEnabled("feature:test"))
    }

    @Test
    fun `capability registry should store disable reason`() {
        val registry: CapabilityRegistry = DefaultCapabilityRegistry()
        registry.disable("feature:missing", "dependency not found")

        assertEquals("dependency not found", registry.reason("feature:missing"))
    }
}
