package studio.singlethread.lib.operations

import studio.singlethread.lib.framework.bukkit.management.STPluginStatus

data class STLibStatusSnapshot(
    val storageBackend: String,
    val plugins: List<STLibStatusPlugin>,
)

data class STLibStatusPlugin(
    val name: String,
    val version: String,
    val status: STPluginStatus,
)
