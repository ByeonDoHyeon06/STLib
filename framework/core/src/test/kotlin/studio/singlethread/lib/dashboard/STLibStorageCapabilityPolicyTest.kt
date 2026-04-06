package studio.singlethread.lib.dashboard

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class STLibStorageCapabilityPolicyTest {
    @Test
    fun `when any storage capability is enabled, reports available`() {
        val policy =
            STLibStorageCapabilityPolicy { capability ->
                capability == "storage:json"
            }

        assertTrue(policy.isStorageAvailable())
    }

    @Test
    fun `when all storage capabilities are disabled, reports unavailable`() {
        val policy = STLibStorageCapabilityPolicy { false }

        assertFalse(policy.isStorageAvailable())
    }
}
