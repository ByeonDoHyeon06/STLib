package studio.singlethread.lib.command

import studio.singlethread.lib.framework.bukkit.management.STPluginSnapshot

class StlibRuntimeSnapshotAssembler(
    private val storageBackend: () -> String,
    private val plugins: () -> List<STPluginSnapshot>,
) {
    fun snapshot(): StlibRuntimeSnapshot {
        return StlibRuntimeSnapshot(
            storageBackend = storageBackend(),
            plugins =
                plugins().map { plugin ->
                    StlibPluginStatus(
                        name = plugin.name,
                        version = plugin.version,
                        status = plugin.status.name.lowercase(),
                    )
                },
        )
    }
}
