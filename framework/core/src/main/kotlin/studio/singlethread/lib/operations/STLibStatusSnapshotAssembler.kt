package studio.singlethread.lib.operations

import studio.singlethread.lib.framework.bukkit.management.STPluginSnapshot

class STLibStatusSnapshotAssembler(
    private val storageBackend: () -> String,
    private val plugins: () -> List<STPluginSnapshot>,
) {
    fun snapshot(): STLibStatusSnapshot {
        return STLibStatusSnapshot(
            storageBackend = storageBackend(),
            plugins =
                plugins().map { plugin ->
                    STLibStatusPlugin(
                        name = plugin.name,
                        version = plugin.version,
                        status = plugin.status,
                    )
                },
        )
    }
}
