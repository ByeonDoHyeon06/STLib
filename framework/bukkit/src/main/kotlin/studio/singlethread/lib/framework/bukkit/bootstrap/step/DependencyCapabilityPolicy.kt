package studio.singlethread.lib.framework.bukkit.bootstrap.step

import studio.singlethread.lib.dependency.common.model.DependencyLoadResult
import studio.singlethread.lib.dependency.common.model.DependencyStatus

internal object DependencyCapabilityPolicy {
    fun isUsable(status: DependencyStatus): Boolean {
        return status == DependencyStatus.LOADED || status == DependencyStatus.PRESENT
    }

    fun isUsable(result: DependencyLoadResult): Boolean {
        return isUsable(result.status)
    }

    fun disableReason(
        result: DependencyLoadResult,
        defaultReason: String,
    ): String {
        return result.message ?: defaultReason
    }
}
