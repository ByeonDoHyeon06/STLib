package studio.singlethread.lib.registry.itemsadder

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ItemsAdderResourceProviderStateTest {
    @Test
    fun `provider should become available after load event`() {
        val provider = ItemsAdderResourceProvider(pluginEnabledChecker = { true })

        assertFalse(provider.isAvailable())
        assertEquals("Waiting for ItemsAdderLoadDataEvent", provider.unavailableReason())

        provider.onUpstreamDataLoaded()

        assertTrue(provider.isAvailable())
        assertEquals(null, provider.unavailableReason())
    }

    @Test
    fun `provider should reset readiness when upstream plugin is disabled`() {
        val provider = ItemsAdderResourceProvider(pluginEnabledChecker = { true })

        provider.onUpstreamDataLoaded()
        assertTrue(provider.isAvailable())

        provider.onUpstreamPluginDisabled()

        assertFalse(provider.isAvailable())
        assertEquals("Waiting for ItemsAdderLoadDataEvent", provider.unavailableReason())
    }
}
