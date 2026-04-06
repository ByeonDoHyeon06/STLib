package studio.singlethread.lib.dashboard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class STLibStatsStoreTest {
    @Test
    fun `load should normalize keys and non-negative values`() {
        val store = newStore()

        store.save(
            mapOf(
                "alpha" to STLibPersistedPluginStats("Alpha", totalEnableCount = 3, totalDisableCount = 1, totalCommandExecuted = 7),
                "beta" to STLibPersistedPluginStats("Beta", totalEnableCount = -2, totalDisableCount = -1, totalCommandExecuted = -8),
            ),
        )

        val loaded = store.load()
        assertEquals(2, loaded.size)
        assertEquals(3, loaded.getValue("alpha").totalEnableCount)
        assertEquals(1, loaded.getValue("alpha").totalDisableCount)
        assertEquals(7, loaded.getValue("alpha").totalCommandExecuted)
        assertEquals(0, loaded.getValue("beta").totalEnableCount)
        assertEquals(0, loaded.getValue("beta").totalDisableCount)
        assertEquals(0, loaded.getValue("beta").totalCommandExecuted)
    }

    private fun newStore(): STLibStatsStore {
        return STLibStatsStore(
            collectionProvider = { InMemoryCollectionStorage("stlib_dashboard_stats") },
            logWarning = {},
        )
    }
}
