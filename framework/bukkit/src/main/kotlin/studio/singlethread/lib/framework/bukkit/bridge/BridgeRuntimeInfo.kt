package studio.singlethread.lib.framework.bukkit.bridge

import studio.singlethread.lib.framework.api.bridge.BridgeMetricsSnapshot
import studio.singlethread.lib.framework.api.bridge.BridgeNodeId
import studio.singlethread.lib.framework.bukkit.config.BridgeMode

data class BridgeRuntimeInfo(
    val mode: BridgeMode,
    val nodeId: BridgeNodeId,
    val namespace: String,
    val requestTimeoutMillis: Long,
    val maxPendingRequests: Int,
    val distributedEnabled: Boolean,
    val redisConnected: Boolean,
    val message: String,
    val metricsProvider: () -> BridgeMetricsSnapshot = { BridgeMetricsSnapshot.EMPTY },
)

fun BridgeRuntimeInfo.metricsSnapshot(): BridgeMetricsSnapshot {
    return metricsProvider()
}
