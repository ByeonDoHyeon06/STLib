package studio.singlethread.lib.command

data class StlibRuntimeSnapshot(
    val storageBackend: String,
    val plugins: List<StlibPluginStatus>,
)

data class StlibPluginStatus(
    val name: String,
    val version: String,
    val status: String,
)
