package studio.singlethread.lib.framework.bukkit.management

import java.time.Instant

data class STPluginSnapshot(
    val name: String,
    val version: String,
    val mainClass: String,
    val status: STPluginStatus,
    val loadedAt: Instant,
    val enabledAt: Instant?,
    val disabledAt: Instant?,
    val enableCount: Int,
    val disableCount: Int,
    val registeredCommandCount: Int,
    val executedCommandCount: Int,
    val lastCommandAt: Instant?,
    val capabilityEnabledCount: Int,
    val capabilityDisabledCount: Int,
    val capabilityUpdatedAt: Instant?,
)
