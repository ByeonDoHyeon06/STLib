package studio.singlethread.lib.registry.oraxen

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OraxenResourceProviderStateTest {
    @Test
    fun `provider should expose plugin-disabled reason`() {
        val provider = OraxenResourceProvider(pluginEnabledChecker = { false })

        assertFalse(provider.isAvailable())
        assertEquals("Oraxen plugin not installed or disabled", provider.unavailableReason())
    }

    @Test
    fun `provider should become available after upstream loaded signal`() {
        val provider = OraxenResourceProvider(pluginEnabledChecker = { true })

        assertFalse(provider.isAvailable())

        provider.onUpstreamDataLoaded()

        assertTrue(provider.isAvailable())
        assertEquals(null, provider.unavailableReason())
    }
}
