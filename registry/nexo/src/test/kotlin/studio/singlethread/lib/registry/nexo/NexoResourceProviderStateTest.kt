package studio.singlethread.lib.registry.nexo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NexoResourceProviderStateTest {
    @Test
    fun `provider should become available after items loaded event`() {
        val provider = NexoResourceProvider(pluginEnabledChecker = { true })

        assertFalse(provider.isAvailable())
        assertEquals("Waiting for NexoItemsLoadedEvent", provider.unavailableReason())

        provider.onUpstreamDataLoaded()

        assertTrue(provider.isAvailable())
        assertEquals(null, provider.unavailableReason())
    }

    @Test
    fun `provider should expose plugin-disabled reason`() {
        val provider = NexoResourceProvider(pluginEnabledChecker = { false })

        assertFalse(provider.isAvailable())
        assertEquals("Nexo plugin not installed or disabled", provider.unavailableReason())
    }
}
