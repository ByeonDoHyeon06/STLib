package studio.singlethread.lib.framework.bukkit.bootstrap.step

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import studio.singlethread.lib.dependency.common.model.DependencyStatus

class DependencyCapabilityPolicyTest {
    @Test
    fun `usable statuses should include loaded and present`() {
        assertTrue(DependencyCapabilityPolicy.isUsable(DependencyStatus.LOADED))
        assertTrue(DependencyCapabilityPolicy.isUsable(DependencyStatus.PRESENT))
    }

    @Test
    fun `non-usable statuses should include skipped disabled and failed`() {
        assertFalse(DependencyCapabilityPolicy.isUsable(DependencyStatus.SKIPPED_DISABLED))
        assertFalse(DependencyCapabilityPolicy.isUsable(DependencyStatus.FAILED))
    }
}
